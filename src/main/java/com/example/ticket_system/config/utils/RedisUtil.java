package com.example.ticket_system.config.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 工具类（带熔断机制）
 */
@Slf4j
@Component
public class RedisUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 暴露 RedisTemplate 供过滤器等需要直接执行 Lua 脚本的场景使用
     */
    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }
    
    // ==================== 熔断器状态 ====================

    /**
     * 熔断器状态机：
     *   CLOSED     → 正常（100% 流量）
     *   OPEN       → 熔断（0% 流量，全部降级）
     *   HALF_OPEN  → 半开（仅少量探测请求通过，每5秒最多3个）
     *   RECOVERING → 渐进恢复（10% → 30% → 50% → 100%，每阶段10秒）
     */
    private enum CircuitState {
        CLOSED,      // 关闭（正常）
        OPEN,        // 打开（熔断）
        HALF_OPEN,   // 半开（探测恢复）
        RECOVERING   // 渐进恢复
    }

    private volatile CircuitState circuitState = CircuitState.CLOSED;

    /** 失败计数器 */
    private final AtomicInteger failureCount = new AtomicInteger(0);

    /** 最后失败时间 */
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    /** 熔断阈值：连续失败次数达到此值时触发熔断 */
    private static final int FAILURE_THRESHOLD = 5;

    /** 熔断恢复时间：熔断后等待多久尝试恢复（毫秒） */
    private static final long RECOVERY_TIMEOUT = 30000; // 30秒

    // ---- HALF_OPEN 后台探活线程 ----
    /** 探活线程执行周期（秒） */
    private static final long PROBE_INTERVAL_SEC = 2;
    /** 探活线程池 */
    private ScheduledExecutorService probeExecutor;
    /** 探活连续成功次数（达到阈值才进入 RECOVERING） */
    private final AtomicInteger probeSuccessCount = new AtomicInteger(0);
    /** 探活连续成功阈值 */
    private static final int PROBE_SUCCESS_THRESHOLD = 2;

    // ---- RECOVERING 渐进恢复控制 ----
    /** 每个恢复阶段持续时间（毫秒） */
    private static final long RECOVERY_PHASE_DURATION = 10000; // 10秒
    /** 恢复阶段开始时间 */
    private volatile long recoveryStartTime = 0;

    /** CLOSED 状态下失败计数衰减时间：超过60秒无失败则清零 */
    private static final long FAILURE_DECAY_MS = 60000;

    // ==================== Redis 恢复回调 ====================

    /** Redis 恢复后的回调列表（如：从 MySQL 重建座位池） */
    private final List<Runnable> recoveryCallbacks = new CopyOnWriteArrayList<>();
    
    /**
     * 设置字符串缓存（永久）
     */
    public void set(String key, String value) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，跳过set操作: key={}", key);
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value);
            onSuccess();
        } catch (Exception e) {
            onFailure(e);
        }
    }
    
    /**
     * 设置字符串缓存（带过期时间）
     */
    public void set(String key, String value, long expireSeconds) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，跳过set操作: key={}", key);
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
            onSuccess();
        } catch (Exception e) {
            onFailure(e);
        }
    }

    /**
     * 尝试获取分布式锁（SETNX）
     *
     * @param key           锁的 key
     * @param value         锁的值（通常放 taskNo 或唯一标识）
     * @param expireSeconds 锁过期时间（秒），防止死锁
     * @return true=获取锁成功，false=锁已被占用
     */
    public boolean setNx(String key, String value, long expireSeconds) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，无法获取锁: key={}", key);
            return false;
        }
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, expireSeconds, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(result)) {
                onSuccess();
                return true;
            } else {
                onSuccess();
                return false;
            }
        } catch (Exception e) {
            onFailure(e);
            return false;
        }
    }

    /**
     * 释放分布式锁（DEL）
     * <p>
     * 注意：仅当锁的值等于传入的 value 时才删除（Lua 脚本保证原子性）
     *
     * @param key   锁的 key
     * @param value 锁的值（用于验证是否是自己的锁）
     */
    public void delete(String key, String value) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，无法释放锁: key={}", key);
            return;
        }
        try {
            // Lua 脚本：GET + DEL 原子执行，防止误删别人的锁
            String luaScript =
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('del', KEYS[1]) " +
                "else " +
                "    return 0 " +
                "end";
            redisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, Long.class),
                    java.util.Collections.singletonList(key),
                    value);
            onSuccess();
        } catch (Exception e) {
            onFailure(e);
        }
    }
    
    /**
     * 获取字符串缓存
     */
    public String get(String key) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，返回null: key={}", key);
            return null;
        }
        try {
            Object value = redisTemplate.opsForValue().get(key);
            onSuccess();
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            onFailure(e);
            return null;
        }
    }
    
    /**
     * 设置 Hash 缓存（永久）
     */
    public void hSet(String key, Map<String, Object> map) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，跳过hSet操作: key={}", key);
            return;
        }
        try {
            redisTemplate.opsForHash().putAll(key, map);
            onSuccess();
        } catch (Exception e) {
            onFailure(e);
        }
    }
    
    /**
     * 设置 Hash 缓存（带过期时间）
     */
    public void hSet(String key, Map<String, Object> map, long expireSeconds) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，跳过hSet操作: key={}", key);
            return;
        }
        try {
            redisTemplate.opsForHash().putAll(key, map);
            redisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
            onSuccess();
        } catch (Exception e) {
            onFailure(e);
        }
    }
    
    /**
     * 获取 Hash 缓存
     */
    public Map<Object, Object> hGet(String key) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，返回空Map: key={}", key);
            return Map.of();
        }
        try {
            Map<Object, Object> result = redisTemplate.opsForHash().entries(key);
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(e);
            return Map.of();
        }
    }
    
    /**
     * 删除 Key
     */
    public void delete(String key) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，跳过delete操作: key={}", key);
            return;
        }
        try {
            redisTemplate.delete(key);
            onSuccess();
        } catch (Exception e) {
            onFailure(e);
        }
    }
    
    /**
     * 判断 Key 是否存在
     */
    public boolean hasKey(String key) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，返回false: key={}", key);
            return false;
        }
        try {
            boolean result = Boolean.TRUE.equals(redisTemplate.hasKey(key));
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(e);
            return false;
        }
    }
    
    /**
     * 向列表右侧添加元素
     */
    public void rPush(String key, String value) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，跳过rPush操作: key={}", key);
            return;
        }
        try {
            redisTemplate.opsForList().rightPush(key, value);
            onSuccess();
        } catch (Exception e) {
            onFailure(e);
        }
    }
    
    /**
     * 获取列表所有元素
     */
    public List<Object> lRange(String key, long start, long end) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，返回空List: key={}", key);
            return List.of();
        }
        try {
            List<Object> result = redisTemplate.opsForList().range(key, start, end);
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(e);
            return List.of();
        }
    }
    
    /**
     * 删除列表
     */
    public void deleteList(String key) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，跳过deleteList操作: key={}", key);
            return;
        }
        try {
            redisTemplate.delete(key);
            onSuccess();
        } catch (Exception e) {
            onFailure(e);
        }
    }
    
    /**
     * 向集合添加元素
     */
    public void sAdd(String key, String... members) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，跳过sAdd操作: key={}", key);
            return;
        }
        try {
            redisTemplate.opsForSet().add(key, members);
            onSuccess();
        } catch (Exception e) {
            onFailure(e);
        }
    }
    
    /**
     * 从集合移除元素
     */
    public void sRemove(String key, Object... members) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，跳过sRemove操作: key={}", key);
            return;
        }
        try {
            redisTemplate.opsForSet().remove(key, members);
            onSuccess();
        } catch (Exception e) {
            onFailure(e);
        }
    }
    
    /**
     * 获取集合所有成员
     */
    public Set<Object> sMembers(String key) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，返回空Set: key={}", key);
            return Set.of();
        }
        try {
            Set<Object> result = redisTemplate.opsForSet().members(key);
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(e);
            return Set.of();
        }
    }
    
    /**
     * 从集合随机弹出指定数量的元素（原子操作）
     */
    public List<Object> sPop(String key, long count) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，返回空List: key={}", key);
            return List.of();
        }
        try {
            List<Object> result = redisTemplate.opsForSet().pop(key, count);
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(e);
            return List.of();
        }
    }
    
    /**
     * 获取集合大小
     */
    public Long sCard(String key) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，返回0: key={}", key);
            return 0L;
        }
        try {
            Long result = redisTemplate.opsForSet().size(key);
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(e);
            return 0L;
        }
    }
    
    /**
     * 从 Hash 中删除指定字段
     */
    public void hDel(String key, Object... hashKeys) {
        if (shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，跳过hDel操作: key={}", key);
            return;
        }
        try {
            redisTemplate.opsForHash().delete(key, hashKeys);
            onSuccess();
        } catch (Exception e) {
            onFailure(e);
        }
    }
    
    // ==================== 熔断器控制方法 ====================

    /**
     * 注册 Redis 恢复回调
     * <p>
     * 当熔断器从 RECOVERING → CLOSED（Redis 完全恢复）时，异步执行回调。
     * 用于从 MySQL 重建 Redis 缓存数据（如座位池）。
     *
     * @param callback 恢复回调
     */
    public void registerRecoveryCallback(Runnable callback) {
        recoveryCallbacks.add(callback);
        log.info("[Redis熔断] 已注册恢复回调: {}", callback.getClass().getName());
    }

    /**
     * 触发所有恢复回调（异步执行，不阻塞用户请求）
     */
    private void fireRecoveryCallbacks() {
        if (recoveryCallbacks.isEmpty()) {
            return;
        }
        log.info("[Redis熔断] Redis已恢复，开始执行{}个恢复回调（重建缓存数据）", recoveryCallbacks.size());
        for (Runnable callback : recoveryCallbacks) {
            new Thread(() -> {
                try {
                    callback.run();
                } catch (Exception e) {
                    log.error("[Redis熔断] 恢复回调执行异常", e);
                }
            }, "redis-recovery").start();
        }
    }

    /**
     * 初始化后台探活线程
     * <p>
     * 线程每 2 秒执行一次，仅在 HALF_OPEN 状态下进行 ping 探活。
     * 用户请求在 HALF_OPEN 状态全部降级，不承担探测任务。
     */
    @PostConstruct
    public void initProbeThread() {
        probeExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "redis-probe-thread");
            t.setDaemon(true);
            return t;
        });
        probeExecutor.scheduleAtFixedRate(this::probeTask, PROBE_INTERVAL_SEC, PROBE_INTERVAL_SEC, TimeUnit.SECONDS);
        log.info("[Redis熔断] 后台探活线程已启动，周期={}秒", PROBE_INTERVAL_SEC);
    }

    @PreDestroy
    public void destroy() {
        if (probeExecutor != null) {
            probeExecutor.shutdown();
            log.info("[Redis熔断] 后台探活线程已关闭");
        }
    }

    /**
     * 后台探活任务：仅在 HALF_OPEN 状态下执行 ping
     */
    private void probeTask() {
        if (circuitState != CircuitState.HALF_OPEN) {
            return;
        }
        try {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            String pong = connection.ping();
            connection.close();
            if ("PONG".equalsIgnoreCase(pong)) {
                int success = probeSuccessCount.incrementAndGet();
                log.info("[Redis熔断] 探活成功({}/{})，ping=PONG", success, PROBE_SUCCESS_THRESHOLD);
                if (success >= PROBE_SUCCESS_THRESHOLD) {
                    onProbeSuccess();
                }
            } else {
                onProbeFailure();
            }
        } catch (Exception e) {
            onProbeFailure();
        }
    }

    /**
     * 探活成功处理：连续成功达阈值 → 进入渐进恢复
     */
    private void onProbeSuccess() {
        this.circuitState = CircuitState.RECOVERING;
        this.recoveryStartTime = System.currentTimeMillis();
        this.failureCount.set(0);
        this.probeSuccessCount.set(0);
        log.info("[Redis熔断] HALF_OPEN → RECOVERING，探活连续成功{}次，开始渐进恢复", PROBE_SUCCESS_THRESHOLD);
    }

    /**
     * 探活失败处理：重新熔断
     */
    private void onProbeFailure() {
        this.circuitState = CircuitState.OPEN;
        this.lastFailureTime.set(System.currentTimeMillis());
        this.probeSuccessCount.set(0);
        log.warn("[Redis熔断] HALF_OPEN → OPEN，探活失败，重新熔断");
    }

    /**
     * 判断是否需要降级（熔断器打开时返回true）
     * <p>
     * 状态流转：
     *   CLOSED     → 失败达阈值 → OPEN
     *   OPEN       → 超时后     → HALF_OPEN（启动后台探活线程 ping）
     *   HALF_OPEN  → 用户请求全部降级，由后台探活线程决定下一步
     *   HALF_OPEN  → 探活连续成功 → RECOVERING
     *   HALF_OPEN  → 探活失败   → OPEN（重新熔断）
     *   RECOVERING → 恢复完成   → CLOSED
     *   RECOVERING → 失败       → OPEN（重新熔断）
     */
    public boolean shouldDegraded() {
        CircuitState state = this.circuitState;
        long now = System.currentTimeMillis();

        switch (state) {
            case CLOSED:
                // 正常状态，放行全部流量
                // 衰减：超过60秒无失败，清零失败计数
                if (failureCount.get() > 0 && now - lastFailureTime.get() >= FAILURE_DECAY_MS) {
                    failureCount.set(0);
                }
                return false;

            case OPEN:
                // 熔断状态，检查是否可以进入半开探测
                if (now - lastFailureTime.get() >= RECOVERY_TIMEOUT) {
                    this.circuitState = CircuitState.HALF_OPEN;
                    this.probeSuccessCount.set(0);
                    log.info("[Redis熔断] OPEN → HALF_OPEN，等待后台探活线程探测");
                }
                return true;

            case HALF_OPEN:
                // 半开状态：用户请求全部降级，由后台探活线程 ping 探活
                return true;

            case RECOVERING:
                // 渐进恢复：按时间阶段逐步放行流量
                long elapsed = now - recoveryStartTime;
                double trafficRatio;
                if (elapsed < RECOVERY_PHASE_DURATION) {
                    trafficRatio = 0.10; // 0-10秒：10%流量
                } else if (elapsed < RECOVERY_PHASE_DURATION * 2) {
                    trafficRatio = 0.30; // 10-20秒：30%流量
                } else if (elapsed < RECOVERY_PHASE_DURATION * 3) {
                    trafficRatio = 0.50; // 20-30秒：50%流量
                } else {
                    // 30秒后：100%流量，恢复完成
                    this.circuitState = CircuitState.CLOSED;
                    failureCount.set(0);
                    log.info("[Redis熔断] RECOVERING → CLOSED，渐进恢复完成");
                    // 触发恢复回调（异步重建缓存数据）
                    fireRecoveryCallbacks();
                    return false;
                }
                // 按比例随机放行
                boolean allow = Math.random() < trafficRatio;
                if (!allow) {
                    log.debug("[Redis熔断] RECOVERING 阶段降级（流量比例={}）", trafficRatio);
                }
                return !allow;

            default:
                return false;
        }
    }

    /**
     * 调用成功时的处理
     */
    private void onSuccess() {
        CircuitState state = this.circuitState;

        if (state == CircuitState.RECOVERING) {
            // 恢复阶段成功，不重置（让时间窗口自然推进到100%）
            return;
        }
        // CLOSED 状态：不重置 failureCount（修复间歇性失败永远不熔断的bug）
        // 仅在超过 FAILURE_DECAY_MS 无失败时由 shouldDegraded() 衰减清零
    }

    /**
     * 调用失败时的处理
     */
    private void onFailure(Exception e) {
        CircuitState state = this.circuitState;
        int count = failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        if (state == CircuitState.RECOVERING) {
            // 恢复阶段失败 → 重新熔断
            this.circuitState = CircuitState.OPEN;
            log.error("[Redis熔断] RECOVERING → OPEN，恢复阶段失败，重新熔断: {}", e.getMessage());

        } else if (state == CircuitState.CLOSED && count >= FAILURE_THRESHOLD) {
            // 正常状态失败次数达到阈值 → 触发熔断
            this.circuitState = CircuitState.OPEN;
            log.error("[Redis熔断] CLOSED → OPEN，连续失败{}次，触发熔断: {}", FAILURE_THRESHOLD, e.getMessage());

        } else {
            log.warn("[Redis熔断] 操作失败(第{}次): {}", count, e.getMessage());
        }
    }

    /**
     * 获取当前熔断器状态
     */
    public String getCircuitState() {
        return circuitState.name();
    }

    /**
     * 获取熔断器详细状态（用于监控）
     */
    public String getCircuitStateDetail() {
        CircuitState state = this.circuitState;
        StringBuilder sb = new StringBuilder();
        sb.append("state=").append(state.name());
        sb.append(", failureCount=").append(failureCount.get());
        sb.append(", lastFailureTime=").append(lastFailureTime.get());

        switch (state) {
            case HALF_OPEN:
                sb.append(", probeSuccessCount=").append(probeSuccessCount.get())
                  .append("/").append(PROBE_SUCCESS_THRESHOLD);
                break;
            case RECOVERING:
                long elapsed = System.currentTimeMillis() - recoveryStartTime;
                double ratio;
                if (elapsed < RECOVERY_PHASE_DURATION) ratio = 0.10;
                else if (elapsed < RECOVERY_PHASE_DURATION * 2) ratio = 0.30;
                else if (elapsed < RECOVERY_PHASE_DURATION * 3) ratio = 0.50;
                else ratio = 1.0;
                sb.append(", recoveryElapsed=").append(elapsed).append("ms")
                  .append(", trafficRatio=").append(ratio);
                break;
            default:
                break;
        }
        return sb.toString();
    }

    /**
     * 手动重置熔断器
     */
    public void resetCircuit() {
        circuitState = CircuitState.CLOSED;
        failureCount.set(0);
        probeSuccessCount.set(0);
        recoveryStartTime = 0;
        log.info("[Redis熔断] 熔断器已手动重置");
    }
    
    /**
     * 对象转 JSON 字符串
     */
    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }
    
    /**
     * JSON 字符串转对象
     */
    public <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }
}
