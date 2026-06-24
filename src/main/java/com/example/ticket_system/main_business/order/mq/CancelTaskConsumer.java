package com.example.ticket_system.main_business.order.mq;

import com.example.ticket_system.main_business.order.service.OrderCancelTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 取消事件消费者
 * <p>
 * 监听 order.cancel.event.queue，消费取消任务并执行。
 * 所有来源（用户主动/超时/管理员）创建的取消任务均由此消费。
 * 与 {@link com.example.ticket_system.main_business.order.schedule.CancelTaskScanner} 共用 executeTask 方法，确保幂等。
 */
@Slf4j
@Component
@RabbitListener(queues = "order.cancel.event.queue")
public class CancelTaskConsumer {

    @Autowired
    private OrderCancelTaskService cancelTaskService;

    @RabbitHandler
    public void handleCancelEvent(String taskNo) {
        log.info("收到取消事件 MQ 消息：taskNo={}", taskNo);

        try {
            cancelTaskService.executeTask(taskNo);
            log.info("MQ 取消任务处理完成：taskNo={}", taskNo);
        } catch (Exception e) {
            log.error("MQ 取消任务处理失败：taskNo={}", taskNo, e);
            // 抛出异常触发 RabbitMQ 消费者重试，重试耗尽进入 DLQ
            throw e;
        }
    }
}