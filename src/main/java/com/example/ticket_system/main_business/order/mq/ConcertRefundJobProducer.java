package com.example.ticket_system.main_business.order.mq;

import com.example.ticket_system.config.utils.RabbitMQUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 批量退款任务 MQ 生产者
 * <p>
 * 创建 ConcertRefundJob 后，发送 MQ 消息立即触发处理。
 */
@Slf4j
@Component
public class ConcertRefundJobProducer {

    @Autowired
    private RabbitMQUtil rabbitMQUtil;

    /**
     * 发送批量退款任务事件
     *
     * @param jobId 任务ID
     */
    public void sendRefundJobEvent(Long jobId) {
        rabbitMQUtil.convertAndSend("order.exchange", "order.refund-job.event", jobId);
        log.info("已发送批量退款任务事件：jobId={}", jobId);
    }
}