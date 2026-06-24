package com.example.ticket_system.main_business.order.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticket_system.config.utils.RedisKeyConstants;
import com.example.ticket_system.config.utils.RedisUtil;
import com.example.ticket_system.main_business.order.entity.OrderCancelTask;
import com.example.ticket_system.main_business.order.entity.OrderInfo;
import com.example.ticket_system.main_business.order.mapper.OrderMapper;
import com.example.ticket_system.main_business.order.service.OrderCancelTaskService;
import com.example.ticket_system.main_business.order.service.SeatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 订单超时扫描定时任务（兜底策略）
 * <p>
 * 每1分钟扫描一次超时未支付订单，创建超时取消任务。
 * MQ 延迟消息是主要手段，此定时任务作为兜底，防止 MQ 消息丢失。
 * 创建的任务由 CancelTaskConsumer（MQ）或 CancelTaskScanner（定时）执行。
 * <p>
 * 扫描到超时订单时，会<strong>直接释放 Redis 座位</strong>（幂等操作），
 * 作为 cancel task 执行前的快速兜底，防止 MQ/定时任务双重失效导致座位永久锁定。
 */
@Slf4j
@Component
public class OrderTimeoutScanner {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderCancelTaskService cancelTaskService;

    @Autowired
    private SeatService seatService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 每60秒执行一次，扫描超时未支付订单并创建取消任务
     */
    @Scheduled(fixedRate = 60000)
    public void scanTimeoutOrders() {
        LocalDateTime now = LocalDateTime.now();

        // 查询所有待支付(0)/支付中(1)且已超时的订单
        List<OrderInfo> timeoutOrders = orderMapper.selectList(
                new LambdaQueryWrapper<OrderInfo>()
                        .in(OrderInfo::getStatus, 0, 1)
                        .lt(OrderInfo::getExpireTime, now)
                        .orderByAsc(OrderInfo::getCreateTime)
                        .last("LIMIT 200")
        );

        if (timeoutOrders.isEmpty()) {
            return;
        }

        log.info("定时任务扫描到[{}]个超时未支付订单，开始创建取消任务并释放 Redis 座位", timeoutOrders.size());

        int successCount = 0;
        int failCount = 0;
        int releaseCount = 0;
        for (OrderInfo order : timeoutOrders) {
            try {
                // 直接释放 Redis 座位（幂等：即使 cancel task 后续也会执行释放，重复执行无影响）
                List<String> seatLabels = parseSeatLabels(order.getSeatsJson());
                if (!seatLabels.isEmpty()) {
                    try {
                        seatService.releaseSeats(order.getConcertId(), order.getTierId(), seatLabels);
                        releaseCount++;
                        log.info("超时扫描直接释放 Redis 座位：orderNo={}, seats={}", order.getOrderNo(), seatLabels);
                    } catch (Exception e) {
                        log.warn("超时扫描释放 Redis 座位失败（cancel task 会兜底）：orderNo={}", order.getOrderNo(), e);
                    }
                }

                cancelTaskService.createCancelTask(
                        order.getOrderId(), order.getUserId(), OrderCancelTask.TASK_TYPE_TIMEOUT);
                successCount++;
            } catch (Exception e) {
                log.error("创建超时取消任务失败：orderNo={}", order.getOrderNo(), e);
                failCount++;
            }
        }

        log.info("定时任务完成：成功创建[{}]个取消任务，失败[{}]个，直接释放 Redis 座位[{}]个",
                successCount, failCount, releaseCount);
    }

    /**
     * 从 seatsJson 中解析出座位标签列表
     */
    private List<String> parseSeatLabels(String seatsJson) {
        if (seatsJson == null || seatsJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> seatList = objectMapper.readValue(seatsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            return seatList.stream()
                    .map(m -> (String) m.get("seat"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            log.warn("解析 seatsJson 失败: {}", seatsJson, e);
            return Collections.emptyList();
        }
    }

    // ==================== Redis 失败补偿扫描 ====================

    /**
     * Redis 座位释放补偿扫描（每5分钟执行一次）
     * <p>
     * 扫描 MySQL 中已取消的订单，检查 Redis locked Hash 中是否还有对应的座位记录。
     * 如果有，说明之前 Redis 释放失败，从 MySQL 恢复座位到 Redis 可用池。
     * <p>
     * 场景：
     * - Redis 熔断期间，座位释放失败
     * - Redis 网络抖动，座位释放丢失
     * - 程序异常，座位释放未执行
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void scanRedisSeatCompensation() {
        // 检查 Redis 是否可用
        if (redisUtil.shouldDegraded()) {
            log.warn("[Redis补偿扫描] Redis 熔断中，跳过本次扫描");
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 查询最近1小时内取消的订单（座位应该已释放但可能 Redis 失败）
        LocalDateTime oneHourAgo = now.minusHours(1);
        List<OrderInfo> cancelledOrders = orderMapper.selectList(
                new LambdaQueryWrapper<OrderInfo>()
                        .in(OrderInfo::getStatus, 3, 4) // 已取消(3)或已退款(4)
                        .gt(OrderInfo::getUpdateTime, oneHourAgo)
                        .last("LIMIT 500")
        );

        if (cancelledOrders.isEmpty()) {
            return;
        }

        log.info("[Redis补偿扫描] 扫描到[{}]个已取消订单，检查 Redis 座位状态", cancelledOrders.size());

        int compensatedCount = 0;
        int noNeedCount = 0;

        for (OrderInfo order : cancelledOrders) {
            try {
                List<String> seatLabels = parseSeatLabels(order.getSeatsJson());
                if (seatLabels.isEmpty()) {
                    noNeedCount++;
                    continue;
                }

                String lockedKey = String.format(RedisKeyConstants.CONCERT_SEATS_LOCKED,
                        order.getConcertId(), order.getTierId());

                // 检查 Redis locked Hash 中是否还有座位记录
                boolean hasLockedSeats = false;
                for (String seat : seatLabels) {
                    if (Boolean.TRUE.equals(redisUtil.hasKey(lockedKey + ":" + seat))) {
                        hasLockedSeats = true;
                        break;
                    }
                }

                if (hasLockedSeats) {
                    // Redis 中还有锁定记录，说明释放失败，需要补偿
                    seatService.releaseSeats(order.getConcertId(), order.getTierId(), seatLabels);
                    compensatedCount++;
                    log.info("[Redis补偿] 座位已从 Redis 释放：orderNo={}, seats={}", order.getOrderNo(), seatLabels);
                } else {
                    noNeedCount++;
                }
            } catch (Exception e) {
                log.error("[Redis补偿] 补偿失败：orderNo={}", order.getOrderNo(), e);
            }
        }

        log.info("[Redis补偿扫描] 完成：已补偿[{}]个订单，座位已释放[{}]个，无需处理[{}]个",
                compensatedCount, compensatedCount, noNeedCount);
    }
}
