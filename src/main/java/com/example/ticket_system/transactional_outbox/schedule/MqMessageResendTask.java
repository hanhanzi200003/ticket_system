package com.example.ticket_system.transactional_outbox.schedule;

import com.example.ticket_system.transactional_outbox.service.MqMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 本地消息表补发定时任务
 *
 * 每 5 秒扫描一次 mq_message 中 status=0 且 retry_count < 10 的消息，
 * 重新发送到 RabbitMQ。
 * 这是消息不丢失的最后一道防线，与业务代码中的 trySend() 配合使用。
 */
@Slf4j
@Component
public class MqMessageResendTask {

    @Autowired
    private MqMessageService mqMessageService;

    /**
     * 每 5 秒执行一次
     */
    @Scheduled(fixedRate = 5000)
    public void resend() {
        mqMessageService.resendPendingMessages();
    }
}