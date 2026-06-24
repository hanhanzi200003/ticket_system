package com.example.ticket_system.config.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RabbitMQ 工具类（带熔断机制）
 *
 * 功能：
 * - 统一封装 RabbitTemplate 操作
 * - 实现熔断器模式，防止 MQ 不可用时雪崩
 * - 自动检测 MQ 状态，支持降级处理
 */
@Slf4j
@Component
public class RabbitMQUtil {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    // ==================== 熔断器状态 ====================

    /** 熔断器状态：CLOSED(关闭-正常)、OPEN(打开-熔断)、HALF_OPEN(半开-探测) */
    private volatile CircuitState circuitState = CircuitState.CLOSED;

    /** 失败计数器 */
    private final AtomicInteger failureCount = new AtomicInteger(0);

    /** 最后失败时间 */
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    /** 熔断阈值：连续失败次数达到此值时触发熔断 */
    private static final int FAILURE_THRESHOLD = 5;

    /** 熔断恢复时间：熔断后等待多久尝试恢复（毫秒） */
    private static final long RECOVERY_TIMEOUT = 30000; // 30秒

    private enum CircuitState {
        CLOSED,      // 关闭（正常）
        OPEN,        // 打开（熔断）
        HALF_OPEN    // 半开（探测恢复）
    }

    // ==================== 发送消息方法 ====================

    /**
     * 发送消息（使用默认交换机）
     *
     * @param routingKey 路由键
     * @param message    消息内容
     */
    public void convertAndSend(String routingKey, Object message) {
        if (shouldDegraded()) {
            log.warn("[MQ降级] MQ已熔断，跳过发送消息: routingKey={}", routingKey);
            return;
        }
        try {
            rabbitTemplate.convertAndSend(routingKey, message);
            onSuccess();
        } catch (Exception e) {
            onFailure(e);
        }
    }

    /**
     * 发送消息（指定交换机和路由键）
     *
     * @param exchange   交换机名称
     * @param routingKey 路由键
     * @param message    消息内容
     */
    public void convertAndSend(String exchange, String routingKey, Object message) {
        if (shouldDegraded()) {
            log.warn("[MQ降级] MQ已熔断，跳过发送消息: exchange={}, routingKey={}", exchange, routingKey);
            return;
        }
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            onSuccess();
        } catch (Exception e) {
            onFailure(e);
        }
    }

    // ==================== 熔断器控制方法 ====================

    /**
     * 判断是否需要降级（熔断器打开时返回true）
     */
    public boolean shouldDegraded() {
        if (circuitState == CircuitState.CLOSED) {
            return false;
        }
        if (circuitState == CircuitState.OPEN) {
            // 检查是否可以进入半开状态
            if (System.currentTimeMillis() - lastFailureTime.get() >= RECOVERY_TIMEOUT) {
                circuitState = CircuitState.HALF_OPEN;
                log.info("[MQ熔断] 进入半开状态，开始探测恢复");
                return false;
            }
            return true;
        }
        // HALF_OPEN 状态允许请求通过
        return false;
    }

    /**
     * 调用成功时的处理
     */
    private void onSuccess() {
        if (circuitState == CircuitState.HALF_OPEN) {
            // 半开状态下成功，恢复为关闭状态
            circuitState = CircuitState.CLOSED;
            failureCount.set(0);
            log.info("[MQ熔断] 探测成功，MQ服务已恢复，熔断器关闭");
        } else if (circuitState == CircuitState.CLOSED) {
            // 正常状态下成功，重置失败计数
            failureCount.set(0);
        }
    }

    /**
     * 调用失败时的处理
     */
    private void onFailure(Exception e) {
        int count = failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        if (circuitState == CircuitState.HALF_OPEN) {
            // 半开状态下失败，重新熔断
            circuitState = CircuitState.OPEN;
            log.error("[MQ熔断] 半开探测失败，重新熔断: {}", e.getMessage());
        } else if (circuitState == CircuitState.CLOSED && count >= FAILURE_THRESHOLD) {
            // 正常状态下失败次数达到阈值，触发熔断
            circuitState = CircuitState.OPEN;
            log.error("[MQ熔断] 连续失败{}次，触发熔断: {}", FAILURE_THRESHOLD, e.getMessage());
        } else {
            log.warn("[MQ熔断] 操作失败(第{}次): {}", count, e.getMessage());
        }
    }

    /**
     * 获取当前熔断器状态
     */
    public String getCircuitState() {
        return circuitState.name();
    }

    /**
     * 手动重置熔断器
     */
    public void resetCircuit() {
        circuitState = CircuitState.CLOSED;
        failureCount.set(0);
        log.info("[MQ熔断] 熔断器已手动重置");
    }
}
