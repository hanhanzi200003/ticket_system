package com.example.ticket_system.main_business.order.mq;

import com.example.ticket_system.main_business.order.service.ConcertRefundJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 演唱会批量退款任务 MQ 消费者
 * <p>
 * 监听 order.refund-job.event.queue，立即处理批量退款任务。
 * 与 {@link com.example.ticket_system.main_business.order.schedule.ConcertRefundJobScanner} 共用 processJob 方法，乐观锁保证幂等。
 */
@Slf4j
@Component
@RabbitListener(queues = "order.refund-job.event.queue")
public class ConcertRefundJobConsumer {

    @Autowired
    private ConcertRefundJobService refundJobService;

    @RabbitHandler
    public void handleRefundJobEvent(Long jobId) {
        log.info("收到批量退款任务 MQ 消息：jobId={}", jobId);

        try {
            refundJobService.processJob(jobId);
            log.info("MQ 批量退款任务处理完成：jobId={}", jobId);
        } catch (Exception e) {
            log.error("MQ 批量退款任务处理失败：jobId={}", jobId, e);
            // 抛出异常触发 RabbitMQ 消费者重试，重试耗尽进入 DLQ
            throw e;
        }
    }
}