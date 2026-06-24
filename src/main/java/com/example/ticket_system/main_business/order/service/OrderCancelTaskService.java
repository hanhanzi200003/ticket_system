package com.example.ticket_system.main_business.order.service;

import com.example.ticket_system.main_business.order.entity.OrderCancelTask;

import java.util.List;

/**
 * 订单取消任务服务
 * <p>
 * 统一的任务模型：所有取消来源（用户主动/超时/管理员）只做一件事 — 创建任务。
 * 任务由 MQ 消费者执行，本地定时扫描兜底。
 */
public interface OrderCancelTaskService {

    /**
     * 创建取消任务
     * <p>
     * 1. INSERT order_cancel_task（status=PENDING）
     * 2. 发送 MQ 到 order.cancel.event 通知消费
     *
     * @param orderId  订单ID
     * @param userId   用户ID
     * @param taskType 任务类型（TASK_TYPE_TIMEOUT / TASK_TYPE_USER_CANCEL / TASK_TYPE_CONCERT_CANCEL）
     * @return 任务编号（taskNo）
     */
    String createCancelTask(Long orderId, Long userId, Integer taskType);

    /**
     * 执行取消任务（幂等）
     * <p>
     * MQ 消费者和定时扫描共用的核心执行方法：
     * 1. 乐观锁更新 status=0→1（防重复执行）
     * 2. 释放 Redis 座位
     * 3. 更新 DB 订单状态
     * 4. 标记 status=2（成功）或 3（失败）
     *
     * @param taskNo 任务编号
     */
    void executeTask(String taskNo);

    /**
     * 扫描待执行/失败的任务，用于定时任务兜底
     *
     * @param limit 最多取多少条
     * @return 待处理的任务列表
     */
    List<OrderCancelTask> scanPendingTasks(int limit);
}