package com.example.ticket_system.main_business.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.ticket_system.config.utils.SnowflakeIdGenerator;
import com.example.ticket_system.main_business.order.entity.ConcertRefundJob;
import com.example.ticket_system.main_business.order.entity.OrderCancelTask;
import com.example.ticket_system.main_business.order.entity.OrderInfo;
import com.example.ticket_system.main_business.order.entity.RefundTask;
import com.example.ticket_system.main_business.order.mapper.ConcertRefundJobMapper;
import com.example.ticket_system.main_business.order.mapper.OrderMapper;
import com.example.ticket_system.main_business.order.service.ConcertRefundJobService;
import com.example.ticket_system.main_business.order.service.OrderCancelTaskService;
import com.example.ticket_system.main_business.order.service.RefundTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 演唱会批量退款任务服务实现
 * <p>
 * 分批处理设计：
 * - 每批 50 条订单，按 order_id 升序扫描
 * - last_order_id 作为断点，支持续传
 * - 乐观锁防重复执行
 * - MQ 消费者立即处理 + 定时扫描器 60s 兜底
 */
@Slf4j
@Service
public class ConcertRefundJobServiceImpl implements ConcertRefundJobService {

    /** 每批处理订单数 */
    private static final int BATCH_SIZE = 50;

    @Autowired
    private ConcertRefundJobMapper jobMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderCancelTaskService cancelTaskService;

    @Autowired
    private RefundTaskService refundTaskService;

    @Autowired
    private SnowflakeIdGenerator idGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createJob(Long concertId) {
        // 1. 幂等：已存在的待处理任务直接返回
        ConcertRefundJob existing = jobMapper.selectOne(
                new LambdaQueryWrapper<ConcertRefundJob>()
                        .eq(ConcertRefundJob::getConcertId, concertId)
                        .in(ConcertRefundJob::getStatus,
                                ConcertRefundJob.STATUS_PENDING,
                                ConcertRefundJob.STATUS_PROCESSING)
                        .last("LIMIT 1"));
        if (existing != null) {
            log.info("演唱会批量退款任务已存在，幂等返回：concertId={}, jobId={}", concertId, existing.getJobId());
            return existing.getJobId();
        }

        // 2. 统计需要处理的订单总数（所有状态未完结的订单）
        Long totalCount = orderMapper.selectCount(
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getConcertId, concertId)
                        .in(OrderInfo::getStatus, 0, 1, 2));

        if (totalCount == 0) {
            log.info("演唱会无待处理订单，无需创建批量退款任务：concertId={}", concertId);
            return null;
        }

        // 3. 创建任务
        LocalDateTime now = LocalDateTime.now();
        ConcertRefundJob job = new ConcertRefundJob();
        job.setJobId(idGenerator.nextId());
        job.setConcertId(concertId);
        job.setStatus(ConcertRefundJob.STATUS_PENDING);
        job.setTotalCount(totalCount.intValue());
        job.setProcessedCount(0);
        job.setLastOrderId(0L); // 初始断点
        job.setCreateTime(now);
        job.setUpdateTime(now);
        jobMapper.insert(job);

        log.info("演唱会批量退款任务已创建：jobId={}, concertId={}, totalCount={}",
                job.getJobId(), concertId, totalCount);

        return job.getJobId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processJob(Long jobId) {
        // 1. 查询任务
        ConcertRefundJob job = jobMapper.selectById(jobId);
        if (job == null) {
            log.warn("批量退款任务不存在：jobId={}", jobId);
            return;
        }

        // 2. 已完成的任务跳过
        if (job.getStatus() == ConcertRefundJob.STATUS_COMPLETED) {
            log.info("批量退款任务已完成，跳过：jobId={}", jobId);
            return;
        }

        // 3. 乐观锁：PENDING → PROCESSING（初次执行时）
        if (job.getStatus() == ConcertRefundJob.STATUS_PENDING) {
            int updated = jobMapper.update(null, new LambdaUpdateWrapper<ConcertRefundJob>()
                    .eq(ConcertRefundJob::getJobId, jobId)
                    .eq(ConcertRefundJob::getStatus, ConcertRefundJob.STATUS_PENDING)
                    .set(ConcertRefundJob::getStatus, ConcertRefundJob.STATUS_PROCESSING)
                    .set(ConcertRefundJob::getUpdateTime, LocalDateTime.now()));
            if (updated == 0) {
                log.info("批量退款任务已被其他节点执行，跳过：jobId={}", jobId);
                return;
            }
            // 重新查询获取最新数据
            job = jobMapper.selectById(jobId);
        }

        // 4. 分批处理订单
        boolean hasMore = true;
        while (hasMore) {
            // 查询下一批订单（> last_order_id 升序）
            List<OrderInfo> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<OrderInfo>()
                            .eq(OrderInfo::getConcertId, job.getConcertId())
                            .gt(OrderInfo::getOrderId, job.getLastOrderId())
                            .in(OrderInfo::getStatus, 0, 1, 2) // 待处理的状态
                            .orderByAsc(OrderInfo::getOrderId)
                            .last("LIMIT " + BATCH_SIZE));

            if (orders.isEmpty()) {
                // 没有更多订单了，标记完成
                hasMore = false;
                break;
            }

            // 处理这一批订单
            for (OrderInfo order : orders) {
                processOrder(job, order);
            }

            // 更新断点
            Long maxId = orders.stream()
                    .map(OrderInfo::getOrderId)
                    .max(Long::compareTo)
                    .orElse(job.getLastOrderId());
            int processed = job.getProcessedCount() + orders.size();

            job.setLastOrderId(maxId);
            job.setProcessedCount(processed);
            job.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(job);

            log.info("批量退款任务进度：jobId={}, processed={}/{}, lastOrderId={}",
                    jobId, processed, job.getTotalCount(), maxId);

            // 检查是否完成
            if (processed >= job.getTotalCount()) {
                hasMore = false;
            }
        }

        // 5. 标记完成
        if (!hasMore) {
            job.setStatus(ConcertRefundJob.STATUS_COMPLETED);
            job.setUpdateTime(LocalDateTime.now());
            jobMapper.updateById(job);
            log.info("批量退款任务完成：jobId={}, concertId={}, totalCount={}",
                    jobId, job.getConcertId(), job.getTotalCount());
        }
    }

    /**
     * 处理单个订单
     * <p>
     * 已支付(2)→创建 RefundTask（内部会更新订单为已取消）
     * 未支付(0/1)→创建 CancelTask（走现有取消流程）
     */
    private void processOrder(ConcertRefundJob job, OrderInfo order) {
        try {
            if (order.getStatus() == 2) {
                // 已支付：创建退款任务
                refundTaskService.createRefundTask(order, RefundTask.REFUND_TYPE_CONCERT_CANCEL);
                log.debug("批量退款：已创建退款任务 orderNo={}", order.getOrderNo());
            } else {
                // 未支付：创建取消任务
                cancelTaskService.createCancelTask(
                        order.getOrderId(), order.getUserId(), OrderCancelTask.TASK_TYPE_CONCERT_CANCEL);
                log.debug("批量退款：已创建取消任务 orderNo={}", order.getOrderNo());
            }
        } catch (Exception e) {
            log.error("批量退款：处理订单失败 orderNo={}, error={}", order.getOrderNo(), e.getMessage(), e);
            // 不抛出异常：继续处理下一批，失败的订单下次扫描时会重试
        }
    }
}