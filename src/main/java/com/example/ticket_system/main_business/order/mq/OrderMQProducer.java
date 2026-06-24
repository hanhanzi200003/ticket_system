package com.example.ticket_system.main_business.order.mq;

import com.example.ticket_system.config.utils.RabbitMQUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单 MQ 统一生产者
 *
 * 负责发送：
 *   1. 订单持久化消息（Redis锁座后 → 异步落库）
 *   2. 订单延迟取消消息（下单后 TTL=10min）
 *   3. 订单取消持久化消息（Redis释放座位后 → 异步更新数据库）
 */
@Slf4j
@Component
public class OrderMQProducer {

    /** 订单交换机 */
    public static final String ORDER_EXCHANGE = "order.exchange";

    @Autowired
    private RabbitMQUtil rabbitMQUtil;

    /**
     * 发送订单持久化消息（异步落库）
     */
    public void sendPersistMessage(OrderPersistMessage message) {
        rabbitMQUtil.convertAndSend(ORDER_EXCHANGE, "order.persist", message);
        log.info("已发送订单持久化消息：orderNo={}", message.getOrderNo());
    }

    /**
     * 发送订单延迟取消消息（下单后10分钟自动取消）
     *
     * @param orderNo 订单号
     * @param orderId 订单ID
     */
    public void sendDelayCancelMessage(String orderNo, Long orderId) {
        String message = orderId + ":" + orderNo;
        rabbitMQUtil.convertAndSend("order.dlx", "order.delay", message);
        log.info("已发送订单延迟取消消息：orderId={}, orderNo={}", orderId, orderNo);
    }

    /**
     * 发送订单取消持久化消息（异步更新数据库状态 + 归还库存）
     */
    public void sendCancelPersistMessage(OrderCancelPersistMessage message) {
        rabbitMQUtil.convertAndSend(ORDER_EXCHANGE, "order.cancel-persist", message);
        log.info("已发送订单取消持久化消息：orderNo={}, newStatus={}", message.getOrderNo(), message.getNewStatus());
    }
}