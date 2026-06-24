package com.example.ticket_system.main_business.order.mq;

import com.example.ticket_system.main_business.order.entity.OrderInfo;
import com.example.ticket_system.main_business.order.mapper.OrderMapper;
import com.example.ticket_system.main_business.event.mapper.TicketTierMapper;
import com.example.ticket_system.main_business.order.service.SeatInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 订单取消持久化消费者
 *
 * 收到消息后异步更新 MySQL 中的订单状态并归还库存
 * 同时更新 SeatInfo 状态为可售(0)
 */
@Slf4j
@Component
@RabbitListener(queues = "order.cancel-persist.queue")
public class OrderCancelPersistConsumer {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private TicketTierMapper ticketTierMapper;

    @Autowired
    private SeatInfoService seatInfoService;

    @RabbitHandler
    @Transactional(rollbackFor = Exception.class)
    public void handleCancelPersistMessage(OrderCancelPersistMessage msg) {
        log.info("收到订单取消持久化消息：orderNo={}", msg.getOrderNo());

        OrderInfo order = orderMapper.selectById(msg.getOrderId());
        if (order == null) {
            log.warn("订单不存在，跳过取消持久化：orderId={}", msg.getOrderId());
            return;
        }

        // 幂等性检查：如果订单已经不是原始状态，跳过
        Integer originStatus = order.getStatus();
        if (originStatus == null) {
            log.warn("订单状态异常，跳过取消：orderNo={}", msg.getOrderNo());
            return;
        }
        // 只能从待支付(0)或支付中(1)取消
        if (originStatus != 0 && originStatus != 1) {
            log.info("订单当前状态[{}]已不可取消，跳过：orderNo={}", originStatus, msg.getOrderNo());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Integer newStatus = msg.getNewStatus();

        if (originStatus == 0 || originStatus == 1) {
            // 待支付/支付中 → 已取消(3)
            order.setStatus(newStatus != null ? newStatus : 3);
            order.setCancelTime(now);
        }

        order.setUpdateTime(now);
        orderMapper.updateById(order);

        // 归还 MySQL 库存
        if (msg.getTierId() != null && msg.getQuantity() != null && msg.getQuantity() > 0) {
            ticketTierMapper.restoreStock(msg.getTierId(), msg.getQuantity());
        }

        // 更新 SeatInfo 为可售(0)
        if (msg.getSeatLabels() != null && !msg.getSeatLabels().isEmpty()) {
            seatInfoService.batchReleaseSeats(msg.getConcertId(), msg.getTierId(), msg.getSeatLabels());
        }

        log.info("订单取消持久化成功：orderNo={}, 原状态[{}], 新状态[{}]",
                msg.getOrderNo(), originStatus, order.getStatus());
    }
}