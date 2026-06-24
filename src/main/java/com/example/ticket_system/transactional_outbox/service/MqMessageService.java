package com.example.ticket_system.transactional_outbox.service;

import com.example.ticket_system.main_business.order.entity.OrderInfo;
import com.example.ticket_system.main_business.order.mq.OrderCancelPersistMessage;
import com.example.ticket_system.main_business.order.mq.OrderPersistMessage;

/**
 * 本地消息表服务（Transactional Outbox）
 *
 * 核心流程（createOrder 为例）：
 *   1. Redis 锁座
 *   2. 开启事务：
 *        a. INSERT order              ← 写订单表
 *        b. INSERT mq_message(status=0)  ← 写本地消息表
 *   3. 提交事务
 *   4. 尝试发送 MQ
 *        ├─ 成功 → UPDATE mq_message.status=1（标记已发送）
 *        └─ 失败 → 保持 status=0，MqMessageResendTask 定时补发
 */
public interface MqMessageService {

    /** 最大重试次数 */
    int MAX_RETRY_COUNT = 10;

    /** 事件类型常量 */
    String EVENT_ORDER_PERSIST = "ORDER_PERSIST";
    String EVENT_ORDER_CANCEL_PERSIST = "ORDER_CANCEL_PERSIST";

    // ===== 事务写入 =====

    /**
     * [createOrder 专用] 同一事务内：
     *   1. INSERT order（订单表）
     *   2. INSERT mq_message(status=0, event=ORDER_PERSIST)
     *
     * @return messageId
     */
    String insertOrderAndPersistMessage(OrderInfo order, OrderPersistMessage persistMsg);

    /**
     * [cancelOrder / 超时 专用] 事务内写入 mq_message(status=0, event=ORDER_CANCEL_PERSIST)
     *
     * @return messageId
     */
    String insertCancelPersistMessage(Long orderId, OrderCancelPersistMessage cancelMsg);

    // ===== 发送 & 标记 =====

    /**
     * 尝试发送 MQ，成功则标记 status=1，失败则留待补发
     */
    void trySendAndMark(String messageId);

    /**
     * 直接标记消息为已发送（外部已确认 MQ 发送成功时调用）
     */
    void markSent(String messageId);

    /**
     * 定时任务专用：批量补发待发送消息
     */
    void resendPendingMessages();
}