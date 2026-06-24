package com.example.ticket_system.main_business.order.schedule;

import com.example.ticket_system.main_business.order.entity.OrderCancelTask;
import com.example.ticket_system.main_business.order.service.OrderCancelTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 取消任务扫描定时器（兜底策略）
 * <p>
 * 每30秒扫描一次取消任务表，处理 PENDING 或 FAILED 的任务。
 * MQ 消费者是主要手段，此定时任务作为兜底。
 * 与 {@link com.example.ticket_system.main_business.order.mq.CancelTaskConsumer} 共用 executeTask 方法，乐观锁保证幂等。
 */
@Slf4j
@Component
public class CancelTaskScanner {

    /** 每次最多处理的任务数 */
    private static final int BATCH_LIMIT = 50;

    @Autowired
    private OrderCancelTaskService cancelTaskService;

    /**
     * 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void scanPendingTasks() {
        List<OrderCancelTask> pendingTasks = cancelTaskService.scanPendingTasks(BATCH_LIMIT);
        if (pendingTasks.isEmpty()) {
            return;
        }

        log.info("CancelTaskScanner 扫描到[{}]个待处理任务", pendingTasks.size());

        int successCount = 0;
        int failCount = 0;
        for (OrderCancelTask task : pendingTasks) {
            try {
                cancelTaskService.executeTask(task.getTaskNo());
                successCount++;
            } catch (Exception e) {
                log.error("定时任务执行取消失败：taskNo={}", task.getTaskNo(), e);
                failCount++;
            }
        }

        if (failCount > 0) {
            log.warn("CancelTaskScanner 完成：成功[{}]个，失败[{}]个", successCount, failCount);
        } else {
            log.info("CancelTaskScanner 完成：成功[{}]个", successCount);
        }
    }
}