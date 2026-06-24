package com.example.ticket_system.main_business.order.service;

import com.example.ticket_system.main_business.order.entity.OrderInfo;
import com.example.ticket_system.main_business.order.entity.RefundTask;

import java.util.List;

/**
 * 退款任务服务
 * <p>
 * 负责退款任务的创建和执行，与支付网关解耦。
 * 核心原则：
 * - 创建时生成 refund_no 作为幂等键，确保一个 refund_no 只能退款一次
 * - 执行时通过 PaymentGatewayFactory 调用当前激活的支付网关
 * - 失败后支持自动重试
 */
public interface RefundTaskService {

    /**
     * 创建退款任务
     * <p>
     * 1. 生成 refund_no（幂等键）
     * 2. INSERT refund_task（status=0 待退款）
     * 3. 更新订单状态为已取消(3)
     *
     * @param order      已支付订单
     * @param refundType 退款原因（1用户退款 2演唱会取消 3管理员强制退款）
     * @return refundNo
     */
    String createRefundTask(OrderInfo order, Integer refundType);

    /**
     * 执行退款任务
     * <p>
     * 1. 乐观锁：status=0 → 1（防重复执行）
     * 2. 调用支付网关发起退款
     * 3. 成功 → 更新退款任务 status=2 + 订单 status=4（已退款）
     * 4. 失败 → 更新退款任务 status=3，记录失败原因（后续重试）
     *
     * @param refundNo 退款任务号
     */
    void executeRefund(String refundNo);

    /**
     * 扫描可重试的退款任务
     *
     * @param limit 最多取多少条
     * @return 待重试的退款任务列表
     */
    List<RefundTask> scanRetryTasks(int limit);
}