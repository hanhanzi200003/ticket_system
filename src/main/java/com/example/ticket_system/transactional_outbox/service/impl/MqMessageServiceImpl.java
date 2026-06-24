package com.example.ticket_system.transactional_outbox.service.impl;

import com.example.ticket_system.main_business.order.entity.OrderInfo;
import com.example.ticket_system.main_business.order.mapper.OrderMapper;
import com.example.ticket_system.main_business.order.mq.OrderCancelPersistMessage;
import com.example.ticket_system.main_business.order.mq.OrderMQProducer;
import com.example.ticket_system.main_business.order.mq.OrderPersistMessage;
import com.example.ticket_system.transactional_outbox.entity.MqMessage;
import com.example.ticket_system.transactional_outbox.mapper.MqMessageMapper;
import com.example.ticket_system.transactional_outbox.service.MqMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 本地消息表服务实现
 *
 * 核心流程（createOrder 为例）：
 *   1. Redis 锁座（同步）
 *   2. insertOrderAndPersistMessage() — 事务内：INSERT order + INSERT mq_message(status=0)
 *   3. 事务提交
 *   4. trySendAndMark() — 尝试发 MQ，成功则 status=1，失败留待定时补发
 */
@Slf4j
@Service
public class MqMessageServiceImpl implements MqMessageService {

    @Autowired
    private MqMessageMapper mqMessageMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderMQProducer orderMQProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String insertOrderAndPersistMessage(OrderInfo order, OrderPersistMessage persistMsg) {
        // 幂等：订单已存在则跳过（防止 Redis 锁座后重复提交）
        OrderInfo exist = orderMapper.selectByOrderNo(order.getOrderNo());
        if (exist != null) {
            log.warn("订单已存在，跳过：orderNo={}", order.getOrderNo());
            return null;
        }

        // 1. 写订单表
        orderMapper.insert(order);

        // 2. 写本地消息表
        String messageId = UUID.randomUUID().toString().replace("-", "");
        MqMessage record = new MqMessage();
        record.setMessageId(messageId);
        record.setEventType(EVENT_ORDER_PERSIST);
        record.setBusinessId(order.getOrderId());
        record.setContent(toJson(persistMsg));
        record.setStatus(0);
        record.setRetryCount(0);
        record.setCreateTime(LocalDateTime.now());
        mqMessageMapper.insert(record);

        log.info("事务写入完成：orderNo={}, messageId={}, eventType=ORDER_PERSIST",
                order.getOrderNo(), messageId);
        return messageId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String insertCancelPersistMessage(Long orderId, OrderCancelPersistMessage cancelMsg) {
        String messageId = UUID.randomUUID().toString().replace("-", "");

        MqMessage record = new MqMessage();
        record.setMessageId(messageId);
        record.setEventType(EVENT_ORDER_CANCEL_PERSIST);
        record.setBusinessId(orderId);
        record.setContent(toJson(cancelMsg));
        record.setStatus(0);
        record.setRetryCount(0);
        record.setCreateTime(LocalDateTime.now());
        mqMessageMapper.insert(record);

        log.info("事务写入完成：orderId={}, messageId={}, eventType=ORDER_CANCEL_PERSIST",
                orderId, messageId);
        return messageId;
    }

    @Override
    public void trySendAndMark(String messageId) {
        if (messageId == null) return;

        try {
            // 从 DB 读取消息体
            MqMessage record = mqMessageMapper.selectByMessageId(messageId);
            if (record == null || record.getStatus() != 0) return;

            // 发 MQ
            boolean success = doSend(record);
            if (success) {
                markSent(messageId);
            }
            // 失败则保持 status=0，留待 MqMessageResendTask 补发
        } catch (Exception e) {
            log.warn("trySendAndMark 失败，留待补发：messageId={}", messageId, e);
        }
    }

    @Override
    public void markSent(String messageId) {
        int updated = mqMessageMapper.markSent(messageId);
        if (updated > 0) {
            log.info("消息标记已发送：messageId={}", messageId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resendPendingMessages() {
        List<MqMessage> pendingList = mqMessageMapper.selectPendingMessages(MAX_RETRY_COUNT, 50);
        if (pendingList.isEmpty()) {
            return;
        }

        log.info("本地消息表补发任务：扫描到[{}]条待发送消息", pendingList.size());

        for (MqMessage msg : pendingList) {
            try {
                boolean success = doSend(msg);
                if (success) {
                    msg.setStatus(1);
                    msg.setRetryCount(msg.getRetryCount() + 1);
                    msg.setUpdateTime(LocalDateTime.now());
                    mqMessageMapper.updateById(msg);
                    log.info("补发成功：messageId={}, eventType={}", msg.getMessageId(), msg.getEventType());
                } else {
                    msg.setRetryCount(msg.getRetryCount() + 1);
                    if (msg.getRetryCount() >= MAX_RETRY_COUNT) {
                        msg.setStatus(2);
                        msg.setErrorMsg("超过最大重试次数(" + MAX_RETRY_COUNT + ")");
                    }
                    msg.setUpdateTime(LocalDateTime.now());
                    mqMessageMapper.updateById(msg);
                }
            } catch (Exception e) {
                log.error("补发异常：messageId={}", msg.getMessageId(), e);
            }
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 根据 event_type 反序列化并发送 MQ
     */
    private boolean doSend(MqMessage record) {
        try {
            String eventType = record.getEventType();
            String content = record.getContent();

            switch (eventType) {
                case EVENT_ORDER_PERSIST: {
                    OrderPersistMessage msg = objectMapper.readValue(content, OrderPersistMessage.class);
                    orderMQProducer.sendPersistMessage(msg);
                    return true;
                }
                case EVENT_ORDER_CANCEL_PERSIST: {
                    OrderCancelPersistMessage msg = objectMapper.readValue(content, OrderCancelPersistMessage.class);
                    orderMQProducer.sendCancelPersistMessage(msg);
                    return true;
                }
                default:
                    log.warn("未知事件类型：{}", eventType);
                    return false;
            }
        } catch (Exception e) {
            log.warn("doSend 失败：messageId={}, eventType={}", record.getMessageId(), record.getEventType(), e);
            return false;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }
}