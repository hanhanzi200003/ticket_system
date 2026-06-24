package com.example.ticket_system.main_business.order.service.impl;

import com.example.ticket_system.config.utils.RedisKeyConstants;
import com.example.ticket_system.config.utils.RedisUtil;
import com.example.ticket_system.main_business.event.entity.SeatInfo;
import com.example.ticket_system.main_business.event.mapper.SeatInfoMapper;
import com.example.ticket_system.main_business.order.service.SeatService;
import com.example.ticket_system.main_business.order.vo.SeatLockResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 座位服务实现类（Redis Lua 脚本实现）
 *
 * 座位标签命名规则：{区域名}-{4位序号}
 * 示例：A区-0001, A区-0002, B区-0001
 */
@Slf4j
@Service
public class SeatServiceImpl implements SeatService {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SeatInfoMapper seatInfoMapper;

    /** 锁座 Lua 脚本 */
    private DefaultRedisScript<List> lockSeatsScript;

    /** 释放座位 Lua 脚本 */
    private DefaultRedisScript<Long> releaseSeatsScript;

    @PostConstruct
    public void init() {
        // 加载锁座脚本
        lockSeatsScript = new DefaultRedisScript<>();
        lockSeatsScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/lockSeats.lua")));
        lockSeatsScript.setResultType(List.class);

        // 加载释放座位脚本
        releaseSeatsScript = new DefaultRedisScript<>();
        releaseSeatsScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/releaseSeats.lua")));
        releaseSeatsScript.setResultType(Long.class);

        // 注册 Redis 恢复回调：Redis 恢复后从 MySQL 重建座位池
        redisUtil.registerRecoveryCallback(this::restoreSeatsFromDb);

        log.info("座位 Lua 脚本加载完成，已注册 Redis 恢复回调");
    }

    @Override
    public void initSeats(Long concertId, Long tierId, String areaName, int totalStock) {
        String availableKey = String.format(RedisKeyConstants.CONCERT_SEATS_AVAILABLE, concertId, tierId);

        // 生成座位标签并批量写入 Redis Set
        int batchSize = 500;
        List<String> batch = new ArrayList<>(batchSize);
        for (int i = 1; i <= totalStock; i++) {
            String seatLabel = areaName + "-" + String.format("%04d", i);
            batch.add(seatLabel);

            if (batch.size() >= batchSize) {
                redisUtil.sAdd(availableKey, batch.toArray(new String[0]));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            redisUtil.sAdd(availableKey, batch.toArray(new String[0]));
        }

        log.info("演唱会[{}]票档[{}]座位初始化完成，共生成[{}]个座位", concertId, tierId, totalStock);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SeatLockResultVO lockSeats(Long concertId, Long tierId, int quantity, String orderNo) {
        // 检查 Redis 状态，如果熔断则走降级策略
        if (redisUtil.shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，跳过锁座，走MQ异步降级策略: concertId={}, tierId={}, orderNo={}", 
                    concertId, tierId, orderNo);
            return new SeatLockResultVO(false, null, "REDIS_DEGRADED");
        }

        String availableKey = String.format(RedisKeyConstants.CONCERT_SEATS_AVAILABLE, concertId, tierId);
        String lockedKey = String.format(RedisKeyConstants.CONCERT_SEATS_LOCKED, concertId, tierId);

        try {
            // 执行 Lua 脚本（原子操作：检查库存 → SPOP 取座 → HSET 锁定）
            List<Object> result = stringRedisTemplate.execute(
                    lockSeatsScript,
                    List.of(availableKey, lockedKey),
                    String.valueOf(quantity),
                    orderNo
            );

            if (result == null || result.isEmpty()) {
                log.error("锁座 Lua 脚本返回空结果");
                return new SeatLockResultVO(false, null, "锁座失败，请重试");
            }

            // 第一个元素是状态码: "1"=成功, "0"=失败
            String statusStr = result.get(0) != null ? result.get(0).toString() : "0";
            if (!"1".equals(statusStr)) {
                long availableCount = result.size() > 1 ? toLong(result.get(1), 0L) : 0L;
                return new SeatLockResultVO(false, null,
                        "座位不足，需要" + quantity + "个，可用" + availableCount + "个");
            }

            // 剩余元素是座位标签
            List<String> seatLabels = result.subList(1, result.size()).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());

            log.info("订单[{}]锁定座位[{}]个：{}", orderNo, seatLabels.size(), seatLabels);
            return new SeatLockResultVO(true, seatLabels, null);
        } catch (Exception e) {
            log.error("锁座 Lua 脚本执行异常，触发降级", e);
            return new SeatLockResultVO(false, null, "REDIS_DEGRADED");
        }
    }

    @Override
    public void releaseSeats(Long concertId, Long tierId, List<String> seatLabels) {
        if (seatLabels == null || seatLabels.isEmpty()) {
            return;
        }

        // 检查 Redis 状态，如果熔断则跳过释放
        if (redisUtil.shouldDegraded()) {
            log.warn("[Redis降级] Redis已熔断，跳过释放座位: concertId={}, tierId={}", concertId, tierId);
            return;
        }

        String availableKey = String.format(RedisKeyConstants.CONCERT_SEATS_AVAILABLE, concertId, tierId);
        String lockedKey = String.format(RedisKeyConstants.CONCERT_SEATS_LOCKED, concertId, tierId);

        try {
            // 执行 Lua 脚本（原子操作：HDEL 移除锁定 → SADD 归还可用池）
            List<String> keys = List.of(availableKey, lockedKey);
            List<String> args = new ArrayList<>(seatLabels);

            stringRedisTemplate.execute(releaseSeatsScript, keys, args.toArray(new String[0]));

            log.info("释放座位[{}]个：{}", seatLabels.size(), seatLabels);
        } catch (Exception e) {
            log.error("释放座位 Lua 脚本执行异常", e);
        }
    }

    @Override
    public long getAvailableCount(Long concertId, Long tierId) {
        String availableKey = String.format(RedisKeyConstants.CONCERT_SEATS_AVAILABLE, concertId, tierId);
        Long count = redisUtil.sCard(availableKey);
        return count != null ? count : 0;
    }

    @Override
    public void deleteSeats(Long concertId, Long tierId) {
        String availableKey = String.format(RedisKeyConstants.CONCERT_SEATS_AVAILABLE, concertId, tierId);
        String lockedKey = String.format(RedisKeyConstants.CONCERT_SEATS_LOCKED, concertId, tierId);
        redisUtil.delete(availableKey);
        redisUtil.delete(lockedKey);
        log.info("删除演唱会[{}]票档[{}]的座位缓存", concertId, tierId);
    }

    @Override
    public void restoreSeatsFromDb() {
        log.info("[Redis恢复] 开始从 MySQL 重建座位池到 Redis");

        // 1. 查询所有 status=0（可售）的座位
        LambdaQueryWrapper<SeatInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeatInfo::getStatus, 0)
               .select(SeatInfo::getConcertId, SeatInfo::getTierId, SeatInfo::getSeatNo);
        List<SeatInfo> availableSeats = seatInfoMapper.selectList(wrapper);

        if (availableSeats.isEmpty()) {
            log.info("[Redis恢复] MySQL 中无可售座位，无需重建");
            return;
        }

        // 2. 按 concertId+tierId 分组
        Map<String, List<SeatInfo>> grouped = availableSeats.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getConcertId() + ":" + s.getTierId()));

        int totalRestored = 0;
        for (Map.Entry<String, List<SeatInfo>> entry : grouped.entrySet()) {
            String[] ids = entry.getKey().split(":");
            Long concertId = Long.parseLong(ids[0]);
            Long tierId = Long.parseLong(ids[1]);
            List<SeatInfo> seats = entry.getValue();

            String availableKey = String.format(RedisKeyConstants.CONCERT_SEATS_AVAILABLE, concertId, tierId);
            String lockedKey = String.format(RedisKeyConstants.CONCERT_SEATS_LOCKED, concertId, tierId);

            // 3. 清除旧的 Redis 座位数据（防止脏数据残留）
            redisUtil.delete(availableKey);
            redisUtil.delete(lockedKey);

            // 4. 批量写入可售座位到 Redis Set
            String[] seatNos = seats.stream()
                    .map(SeatInfo::getSeatNo)
                    .toArray(String[]::new);

            // 分批写入（每批500个）
            int batchSize = 500;
            for (int i = 0; i < seatNos.length; i += batchSize) {
                int end = Math.min(i + batchSize, seatNos.length);
                String[] batch = new String[end - i];
                System.arraycopy(seatNos, i, batch, 0, end - i);
                redisUtil.sAdd(availableKey, batch);
            }

            totalRestored += seats.size();
            log.info("[Redis恢复] 演唱会[{}]票档[{}]重建座位[{}]个", concertId, tierId, seats.size());
        }

        log.info("[Redis恢复] 座位池重建完成，共重建[{}]个座位，覆盖[{}]个票档",
                totalRestored, grouped.size());
    }

    // ==================== 辅助方法 ====================

    private Long toLong(Object obj) {
        return toLong(obj, null);
    }

    private Long toLong(Object obj, Long defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}