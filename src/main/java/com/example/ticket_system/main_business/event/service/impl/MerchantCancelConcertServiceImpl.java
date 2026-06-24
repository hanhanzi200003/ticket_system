package com.example.ticket_system.main_business.event.service.impl;

import com.example.ticket_system.config.exception.AllException;
import com.example.ticket_system.main_business.event.entity.Concert;
import com.example.ticket_system.main_business.event.mapper.ConcertMapper;
import com.example.ticket_system.main_business.event.service.MerchantCancelConcertService;
import com.example.ticket_system.main_business.order.mq.ConcertRefundJobProducer;
import com.example.ticket_system.main_business.order.service.ConcertRefundJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 商家取消演唱会服务实现
 * <p>
 * 事务设计：
 * <ol>
 *   <li>更新演唱会 → lifecycle_status = OFFLINE(4)</li>
 *   <li>创建 ConcertRefundJob（统计待处理订单数）</li>
 * </ol>
 * 两个操作在同一个事务中，确保一致性。
 * 事务提交后发送 MQ 消息触发立即执行。
 */
@Slf4j
@Service
public class MerchantCancelConcertServiceImpl implements MerchantCancelConcertService {

    @Autowired
    private ConcertMapper concertMapper;

    @Autowired
    private ConcertRefundJobService refundJobService;

    @Autowired
    private ConcertRefundJobProducer refundJobProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelConcert(Long concertId, Long merchantId) {
        // 1. 查询演唱会
        Concert concert = concertMapper.selectById(concertId);
        if (concert == null || concert.getDeleted() == 1) {
            throw new AllException(404, "演唱会不存在");
        }

        // 2. 验证权限
        if (!concert.getMerchantId().equals(merchantId)) {
            throw new AllException(403, "无权操作此演唱会");
        }

        // 3. 验证状态：已结束(3)或已下架(4)不能再取消
        if (concert.getLifecycleStatus() == Concert.LIFECYCLE_FINISHED) {
            throw new AllException(400, "演唱会已结束，无法取消");
        }
        if (concert.getLifecycleStatus() == Concert.LIFECYCLE_OFFLINE) {
            throw new AllException(400, "演唱会已下架，无法取消");
        }

        // 4. 更新演唱会状态为已下架(4)
        concert.setLifecycleStatus(Concert.LIFECYCLE_OFFLINE);
        concert.setUpdateTime(LocalDateTime.now());
        concertMapper.updateById(concert);
        log.info("商家[{}]取消演唱会[{}]，状态已更新为下架", merchantId, concertId);

        // 5. 创建批量退款任务（事务内，与演唱会状态更新一致）
        Long jobId = refundJobService.createJob(concertId);
        if (jobId == null) {
            log.info("演唱会[{}]无待处理订单，无需退款", concertId);
            return;
        }

        log.info("演唱会批量退款任务已创建：jobId={}, concertId={}", jobId, concertId);

        // 6. 事务提交后发送 MQ 消息
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refundJobProducer.sendRefundJobEvent(jobId);
                log.info("已发送批量退款 MQ 消息：jobId={}", jobId);
            }
        });
    }
}