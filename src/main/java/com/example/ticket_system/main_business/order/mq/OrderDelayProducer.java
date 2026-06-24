package com.example.ticket_system.main_business.order.mq;

import com.example.ticket_system.config.utils.RabbitMQUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单延迟取消消息生产者
 *
 * 下单后发送延迟消息，10分钟后自动取消未支付的订单
 */
@Slf4j
@Component
public class OrderDelayProducer {

    /** 延迟队列（消息在此等待 TTL 过期） */
    public static final String DELAY_QUEUE = "order.delay.queue";
    /** 死信交换机 */
    public static final String DLX = "order.dlx";

    @Autowired
    private RabbitMQUtil rabbitMQUtil;

    /**
     * 发送订单取消延迟消息
     *
     * @param orderNo 订单号
     * @param orderId 订单ID
     */
    public void sendCancelMessage(String orderNo, Long orderId) {
        String message = orderId + ":" + orderNo;
        rabbitMQUtil.convertAndSend(DLX, "order.delay", message);
        log.info("已发送订单取消延迟消息：orderId={}, orderNo={}", orderId, orderNo);
    }
}