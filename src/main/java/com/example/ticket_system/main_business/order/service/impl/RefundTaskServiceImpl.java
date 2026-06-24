package com.example.ticket_system.main_business.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.ticket_system.config.utils.SnowflakeIdGenerator;
import com.example.ticket_system.main_business.order.entity.OrderInfo;
import com.example.ticket_system.main_business.order.entity.RefundTask;
import com.example.ticket_system.main_business.order.mapper.OrderMapper;
import com.example.ticket_system.main_business.order.mapper.RefundTaskMapper;
import com.example.ticket_system.main_business.order.service.RefundTaskService;
import com.example.ticket_system.main_business.pay.gateway.PaymentGateway;
import com.example.ticket_system.main_business.pay.gateway.PaymentGatewayFactory;
import com.example.ticket_system.main_business.pay.gateway.RefundRequest;
import com.example.ticket_system.main_business.pay.gateway.RefundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

/**
 * 退款任务服务实现
 * <p>
 * 核心流程：
 * <ol>
 *   <li>创建退款任务（生成 refund_no 作为幂等键）</li>
 *   <li>执行退款（乐观锁防重复 + 支付网关发起退款）</li>
 *   <li>成功 → 更新订单状态为已退款</li>
 *   <li>失败 → 记录失败原因，定时重试</li>
 * </ol>
 */
@Slf4j
@Service
public class RefundTaskServiceImpl implements RefundTaskService {

    @Autowired
    private RefundTaskMapper refundTaskMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private PaymentGatewayFactory gatewayFactory;

    @Autowired
    private SnowflakeIdGenerator idGenerator;

    // ==================== 创建退款任务 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createRefundTask(OrderInfo order, Integer refundType) {
        // 1. 幂等检查：同一订单 + 同一退款类型 已有退款任务直接返回
        RefundTask existing = refundTaskMapper.selectByOrderIdAndRefundType(order.getOrderId(), refundType);
        if (existing != null) {
            log.info("退款任务已存在（唯一约束幂等）：orderId={}, refundType={}, refundNo={}",
                    order.getOrderId(), refundType, existing.getRefundNo());
            return existing.getRefundNo();
        }

        // 2. 生成 refund_no（幂等键）
        String refundNo = generateRefundNo();

        // 3. 构建退款任务
        LocalDateTime now = LocalDateTime.now();
        RefundTask task = new RefundTask();
        task.setId(idGenerator.nextId());
        task.setRefundNo(refundNo);
        task.setOrderId(order.getOrderId());
        task.setUserId(order.getUserId());
        task.setConcertId(order.getConcertId());
        task.setPayTransactionId(order.getPayTransactionId());
        task.setRefundAmount(order.getActualAmount());
        task.setRefundType(refundType);
        task.setStatus(RefundTask.STATUS_PENDING);
        task.setRetryCount(0);
        task.setMaxRetry(5);
        task.setCreateTime(now);
        task.setUpdateTime(now);

        // 4. INSERT 退款任务
        refundTaskMapper.insert(task);

        // 5. 更新订单状态为已取消(3)
        order.setStatus(3);
        order.setUpdateTime(now);
        order.setCancelTime(now);
        orderMapper.updateById(order);

        log.info("退款任务已创建：refundNo={}, orderId={}, orderNo={}, refundType={}, amount={}",
                refundNo, order.getOrderId(), order.getOrderNo(), refundType, order.getActualAmount());

        return refundNo;
    }

    // ==================== 执行退款 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeRefund(String refundNo) {
        // 1. 查询退款任务
        RefundTask task = refundTaskMapper.selectByRefundNo(refundNo);
        if (task == null) {
            log.warn("执行退款：任务不存在 refundNo={}", refundNo);
            return;
        }

        // 2. 乐观锁：只有 PENDING 状态才能执行
        int updated = refundTaskMapper.update(null, new LambdaUpdateWrapper<RefundTask>()
                .eq(RefundTask::getRefundNo, refundNo)
                .eq(RefundTask::getStatus, RefundTask.STATUS_PENDING)
                .set(RefundTask::getStatus, RefundTask.STATUS_PROCESSING)
                .set(RefundTask::getRetryCount, task.getRetryCount() + 1)
                .set(RefundTask::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            log.info("退款任务已被其他节点执行，跳过：refundNo={}", refundNo);
            return;
        }

        // 重新查询（获取最新数据）
        task = refundTaskMapper.selectByRefundNo(refundNo);

        try {
            // 3. 查询订单
            OrderInfo order = orderMapper.selectById(task.getOrderId());
            if (order == null) {
                log.error("退款任务：订单不存在 orderId={}", task.getOrderId());
                markTaskFailed(task, "订单不存在");
                return;
            }

            // 4. 订单已退款的状态不做处理
            if (order.getStatus() == 4) {
                log.info("退款任务：订单已退款，直接标记成功 orderId={}", order.getOrderId());
                markTaskSuccess(task, order, "订单已退款");
                return;
            }

            // 5. 调用支付网关发起退款
            PaymentGateway gateway = gatewayFactory.getGateway();
            RefundRequest refundRequest = RefundRequest.builder()
                    .orderNo(order.getOrderNo())
                    .refundNo(refundNo)
                    .originalTransactionId(task.getPayTransactionId())
                    .refundAmount(task.getRefundAmount())
                    .reason(getRefundReason(task.getRefundType()))
                    .build();

            RefundResponse refundResponse = gateway.refund(refundRequest);

            if (refundResponse.isSuccess() && "SUCCESS".equals(refundResponse.getStatus())) {
                // 退款成功
                markTaskSuccess(task, order, refundResponse.getRefundTransactionId());
            } else {
                // 退款失败（如网关返回明确失败）
                String failMsg = String.format("支付网关退款失败：status=%s, message=%s",
                        refundResponse.getStatus(), refundResponse.getMessage());
                markTaskFailed(task, failMsg);
            }

        } catch (Exception e) {
            log.error("退款任务执行异常：refundNo={}", refundNo, e);
            markTaskFailed(task, e.getMessage());
            // 不抛出异常：事务正常提交，任务标记为 FAILED
            // 后续由定时任务扫描重试
        }
    }

    // ==================== 扫描重试 ====================

    @Override
    public List<RefundTask> scanRetryTasks(int limit) {
        return refundTaskMapper.selectList(
                new LambdaQueryWrapper<RefundTask>()
                        .eq(RefundTask::getStatus, RefundTask.STATUS_FAILED)
                        .lt(RefundTask::getRetryCount, 5) // 最多重试 5 次
                        .apply("next_retry_time IS NULL OR next_retry_time <= NOW()")
                        .orderByAsc(RefundTask::getUpdateTime)
                        .last("LIMIT " + limit));
    }

    // ==================== 私有方法 ====================

    /**
     * 标记退款成功：更新退款任务 + 更新订单状态为已退款
     */
    private void markTaskSuccess(RefundTask task, OrderInfo order, String refundTransactionId) {
        LocalDateTime now = LocalDateTime.now();

        // 更新退款任务
        refundTaskMapper.update(null, new LambdaUpdateWrapper<RefundTask>()
                .eq(RefundTask::getId, task.getId())
                .set(RefundTask::getStatus, RefundTask.STATUS_SUCCESS)
                .set(RefundTask::getRefundTransactionId, refundTransactionId)
                .set(RefundTask::getUpdateTime, now));

        // 更新订单状态为已退款(4)
        order.setStatus(4);
        order.setRefundTime(now);
        order.setUpdateTime(now);
        orderMapper.updateById(order);

        log.info("退款成功：refundNo={}, orderNo={}, refundTransactionId={}",
                task.getRefundNo(), order.getOrderNo(), refundTransactionId);
    }

    /**
     * 标记退款失败
     */
    private void markTaskFailed(RefundTask task, String failReason) {
        LocalDateTime now = LocalDateTime.now();
        int retryCount = task.getRetryCount() + 1;

        // 计算下次重试时间：指数退避（重试次数 * 30秒）
        LocalDateTime nextRetry = now.plusSeconds(retryCount * 30L);

        refundTaskMapper.update(null, new LambdaUpdateWrapper<RefundTask>()
                .eq(RefundTask::getId, task.getId())
                .set(RefundTask::getStatus, RefundTask.STATUS_FAILED)
                .set(RefundTask::getFailReason, failReason)
                .set(RefundTask::getRetryCount, retryCount)
                .set(RefundTask::getNextRetryTime, nextRetry)
                .set(RefundTask::getUpdateTime, now));

        log.warn("退款失败：refundNo={}, retryCount={}, nextRetry={}, failReason={}",
                task.getRefundNo(), retryCount, nextRetry, failReason);
    }

    /**
     * 生成退款任务号：RF + yyyyMMddHHmmss + 4位随机数
     */
    private String generateRefundNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = new Random().nextInt(9000) + 1000;
        return "RF" + timestamp + random;
    }

    /**
     * 根据退款原因类型获取描述
     */
    private String getRefundReason(Integer refundType) {
        switch (refundType) {
            case RefundTask.REFUND_TYPE_USER:
                return "用户申请退款";
            case RefundTask.REFUND_TYPE_CONCERT_CANCEL:
                return "演唱会取消";
            case RefundTask.REFUND_TYPE_ADMIN:
                return "管理员强制退款";
            default:
                return "其他原因";
        }
    }
}