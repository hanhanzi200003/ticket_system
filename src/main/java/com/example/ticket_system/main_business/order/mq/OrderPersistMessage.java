package com.example.ticket_system.main_business.order.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单持久化消息（Redis锁座后 → MQ异步落库）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPersistMessage {

    /** 订单ID（雪花算法预生成） */
    private Long orderId;
    /** 订单号 */
    private String orderNo;
    /** 用户ID */
    private Long userId;
    /** 演唱会ID */
    private Long concertId;
    /** 票档ID */
    private Long tierId;
    /** 优惠券ID */
    private Long couponId;
    /** 数量 */
    private Integer quantity;
    /** 座位JSON */
    private String seatsJson;
    /** 票价 */
    private BigDecimal ticketPrice;
    /** 原价 */
    private BigDecimal originalAmount;
    /** 实付 */
    private BigDecimal actualAmount;
    /** 过期时间 */
    private LocalDateTime expireTime;

    // ===== 快照信息 =====
    private String snapshotConcertName;
    private String snapshotArtistName;
    private String snapshotAreaName;
    private String snapshotTierName;
    private String snapshotVenueName;
    private String snapshotCity;
    private String snapshotCoverUrl;
    private LocalDateTime snapshotConcertTime;

    // ===== 补偿用（锁座失败回滚时需要） =====
    private List<String> seatLabels;
}