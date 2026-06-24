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
 * 订单持久化消费者
 *
 * 收到消息后将订单数据写入 MySQL（异步落库）
 * 同时扣减 MySQL 中的票档可用库存
 * 同时更新 SeatInfo 状态为已售(1)（MySQL 不区分锁定/已售）
 */
@Slf4j
@Component
@RabbitListener(queues = "order.persist.queue")
public class OrderPersistConsumer {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private TicketTierMapper ticketTierMapper;

    @Autowired
    private SeatInfoService seatInfoService;

    @RabbitHandler
    @Transactional(rollbackFor = Exception.class)
    public void handlePersistMessage(OrderPersistMessage msg) {
        log.info("收到订单持久化消息：orderNo={}", msg.getOrderNo());

        // 1. 订单不存在则写入（幂等：新流程下订单已由服务层写入，这里是兜底）
        OrderInfo exist = orderMapper.selectByOrderNo(msg.getOrderNo());
        if (exist != null) {
            log.info("订单已存在，跳过 INSERT：orderNo={}", msg.getOrderNo());
        } else {
            LocalDateTime now = LocalDateTime.now();

            OrderInfo order = new OrderInfo();
            order.setOrderId(msg.getOrderId());
            order.setOrderNo(msg.getOrderNo());
            order.setUserId(msg.getUserId());
            order.setConcertId(msg.getConcertId());
            order.setTierId(msg.getTierId());
            order.setCouponId(msg.getCouponId());
            order.setQuantity(msg.getQuantity());
            order.setSeatsJson(msg.getSeatsJson());
            order.setOriginalAmount(msg.getOriginalAmount());
            order.setActualAmount(msg.getActualAmount());
            order.setStatus(0);
            order.setCreateTime(now);
            order.setExpireTime(msg.getExpireTime());
            order.setUserDeleted(0);
            order.setSnapshotConcertName(msg.getSnapshotConcertName());
            order.setSnapshotArtistName(msg.getSnapshotArtistName());
            order.setSnapshotAreaName(msg.getSnapshotAreaName());
            order.setSnapshotTierName(msg.getSnapshotTierName());
            order.setSnapshotTicketPrice(msg.getTicketPrice());
            order.setSnapshotVenueName(msg.getSnapshotVenueName());
            order.setSnapshotCity(msg.getSnapshotCity());
            order.setSnapshotCoverUrl(msg.getSnapshotCoverUrl());
            order.setSnapshotConcertTime(msg.getSnapshotConcertTime());

            orderMapper.insert(order);
        }

        // 2. 扣减 MySQL 库存（幂等：UPDATE stock = stock - ? WHERE stock >= ?）
        //    首次执行会扣减，重复执行因 stock 已减少不会重复扣减
        int updated = ticketTierMapper.updateStock(msg.getTierId(), msg.getQuantity());
        if (updated > 0) {
            log.info("MySQL库存扣减成功：tierId={}, quantity={}", msg.getTierId(), msg.getQuantity());
        } else {
            log.info("MySQL库存已扣减，跳过（幂等）：tierId={}, quantity={}", msg.getTierId(), msg.getQuantity());
        }

        // 3. 更新 SeatInfo 状态为已售(1)（MySQL 不区分锁定/已售，支付成功时无需再更新）
        if (msg.getSeatLabels() != null && !msg.getSeatLabels().isEmpty()) {
            seatInfoService.batchLockSeats(msg.getConcertId(), msg.getTierId(), msg.getSeatLabels());
        }

        log.info("订单持久化成功：orderNo={}, orderId={}", msg.getOrderNo(), msg.getOrderId());
    }
}