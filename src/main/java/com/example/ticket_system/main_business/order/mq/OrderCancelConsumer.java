package com.example.ticket_system.main_business.order.mq;

import com.example.ticket_system.main_business.order.entity.OrderCancelTask;
import com.example.ticket_system.main_business.order.entity.OrderInfo;
import com.example.ticket_system.main_business.order.mapper.OrderMapper;
import com.example.ticket_system.main_business.order.service.OrderCancelTaskService;
import com.example.ticket_system.main_business.order.service.SeatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 订单超时取消消费者
 * <p>
 * 监听 order.cancel.queue（延迟队列 TTL 过期后经 DLX 转发而来）
 * 收到消息后创建超时取消任务，由 CancelTaskConsumer 或 CancelTaskScanner 执行。
 * <p>
 * 消费消息时，会<strong>直接释放 Redis 座位</strong>（幂等操作），
 * 作为 cancel task 执行前的快速兜底，防止 MQ/定时任务双重失效导致座位永久锁定。
 */
@Slf4j
@Component
@RabbitListener(queues = "order.cancel.queue")
public class OrderCancelConsumer {

    @Autowired
    private OrderCancelTaskService cancelTaskService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private SeatService seatService;

    @Autowired
    private ObjectMapper objectMapper;

    @RabbitHandler
    public void handleCancelMessage(String message) {
        log.info("收到订单超时取消消息：{}", message);

        Long orderId;
        try {
            String[] parts = message.split(":");
            orderId = Long.parseLong(parts[0]);
        } catch (Exception e) {
            log.error("消息格式错误：{}", message, e);
            return;
        }

        try {
            // 查询订单
            OrderInfo order = orderMapper.selectById(orderId);
            if (order == null) {
                log.warn("超时取消：订单不存在 orderId={}", orderId);
                return;
            }
            if (order.getStatus() != 0 && order.getStatus() != 1) {
                log.info("超时取消：订单已处理，跳过 orderNo={}, status={}", order.getOrderNo(), order.getStatus());
                return;
            }

            // 直接释放 Redis 座位（幂等：即使 cancel task 后续也会执行释放，重复执行无影响）
            List<String> seatLabels = parseSeatLabels(order.getSeatsJson());
            if (!seatLabels.isEmpty()) {
                try {
                    seatService.releaseSeats(order.getConcertId(), order.getTierId(), seatLabels);
                    log.info("超时 MQ 直接释放 Redis 座位：orderNo={}, seats={}", order.getOrderNo(), seatLabels);
                } catch (Exception e) {
                    log.warn("超时 MQ 释放 Redis 座位失败（cancel task 会兜底）：orderNo={}", order.getOrderNo(), e);
                }
            }

            // 创建超时取消任务
            String taskNo = cancelTaskService.createCancelTask(
                    orderId, order.getUserId(), OrderCancelTask.TASK_TYPE_TIMEOUT);
            log.info("超时取消任务已创建：taskNo={}", taskNo);

        } catch (Exception e) {
            log.error("MQ 超时取消任务创建失败：message={}", message, e);
            // 抛出异常触发 RabbitMQ 重试，重试耗尽进入 DLQ
            throw e;
        }
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
}
