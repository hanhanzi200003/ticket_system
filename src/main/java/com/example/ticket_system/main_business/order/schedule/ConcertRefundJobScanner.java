package com.example.ticket_system.main_business.order.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticket_system.main_business.order.entity.ConcertRefundJob;
import com.example.ticket_system.main_business.order.mapper.ConcertRefundJobMapper;
import com.example.ticket_system.main_business.order.service.ConcertRefundJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 批量退款任务扫描定时器（兜底策略）
 * <p>
 * 每60秒扫描一次，处理待执行(0)或执行中(1)的批量退款任务。
 * MQ 消费者是主要手段，此定时任务作为兜底，确保断点续传。
 * 与 {@link com.example.ticket_system.main_business.order.mq.ConcertRefundJobConsumer} 共用 processJob 方法，乐观锁保证幂等。
 */
@Slf4j
@Component
public class ConcertRefundJobScanner {

    @Autowired
    private ConcertRefundJobMapper jobMapper;

    @Autowired
    private ConcertRefundJobService refundJobService;

    /**
     * 每60秒执行一次
     */
    @Scheduled(fixedRate = 60000)
    public void scanPendingJobs() {
        // 扫描待开始(0)或执行中(1)的任务
        List<ConcertRefundJob> pendingJobs = jobMapper.selectList(
                new LambdaQueryWrapper<ConcertRefundJob>()
                        .in(ConcertRefundJob::getStatus,
                                ConcertRefundJob.STATUS_PENDING,
                                ConcertRefundJob.STATUS_PROCESSING)
                        .orderByAsc(ConcertRefundJob::getCreateTime)
                        .last("LIMIT 10"));

        if (pendingJobs.isEmpty()) {
            return;
        }

        log.info("ConcertRefundJobScanner 扫描到[{}]个待处理批量退款任务", pendingJobs.size());

        for (ConcertRefundJob job : pendingJobs) {
            try {
                refundJobService.processJob(job.getJobId());
                log.info("ConcertRefundJobScanner 处理完成：jobId={}", job.getJobId());
            } catch (Exception e) {
                log.error("ConcertRefundJobScanner 处理失败：jobId={}", job.getJobId(), e);
            }
        }
    }
}