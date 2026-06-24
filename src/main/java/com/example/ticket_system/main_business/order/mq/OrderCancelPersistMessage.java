package com.example.ticket_system.main_business.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 订单取消持久化消息（Redis释放座位后 → MQ异步更新数据库）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelPersistMessage {

    /** 订单ID */
    private Long orderId;
    /** 订单号 */
    private String orderNo;
    /** 用户ID */
    private Long userId;
    /** 新状态：2=已取消, 3=已退款 */
    private Integer newStatus;
    /** 演唱会ID */
    private Long concertId;
    /** 票档ID */
    private Long tierId;
    /** 数量（归还stock用） */
    private Integer quantity;
    /** 座位标签列表（归还Redis备用） */
    private List<String> seatLabels;
}