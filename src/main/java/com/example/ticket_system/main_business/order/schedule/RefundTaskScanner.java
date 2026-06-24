package com.example.ticket_system.main_business.order.schedule;

import com.example.ticket_system.main_business.order.entity.RefundTask;
import com.example.ticket_system.main_business.order.service.RefundTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 退款任务重试扫描定时器
 * <p>
 * 每60秒扫描一次，处理失败(3)的退款任务，进行重试。
 * 重试超过5次后不再重试，需人工介入。
 */
@Slf4j
@Component
public class RefundTaskScanner {

    /** 每次最多重试的退款任务数 */
    private static final int BATCH_LIMIT = 50;

    @Autowired
    private RefundTaskService refundTaskService;

    /**
     * 每60秒执行一次
     */
    @Scheduled(fixedRate = 60000)
    public void scanFailedRefundTasks() {
        List<RefundTask> failedTasks = refundTaskService.scanRetryTasks(BATCH_LIMIT);
        if (failedTasks.isEmpty()) {
            return;
        }

        log.info("RefundTaskScanner 扫描到[{}]个待重试退款任务", failedTasks.size());

        int successCount = 0;
        int failCount = 0;
        for (RefundTask task : failedTasks) {
            try {
                refundTaskService.executeRefund(task.getRefundNo());
                successCount++;
            } catch (Exception e) {
                log.error("退款任务重试失败：refundNo={}", task.getRefundNo(), e);
                failCount++;
            }
        }

        if (failCount > 0) {
            log.warn("RefundTaskScanner 完成：成功重试[{}]个，仍有[{}]个失败", successCount, failCount);
        } else {
            log.info("RefundTaskScanner 完成：成功重试[{}]个", successCount);
        }
    }
}