package com.example.ticket_system.main_business.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单信息VO（返回给前端）
 */
@Data
public class OrderVO {

    private Long orderId;
    private String orderNo;
    private Long concertId;
    private Long tierId;

    /** 购买数量 */
    private Integer quantity;

    /** 座位信息 */
    private String seatsJson;

    /** 订单原价 */
    private BigDecimal originalAmount;

    /** 实付金额 */
    private BigDecimal actualAmount;

    /** 订单状态：0待支付 1支付中 2已支付 3已取消 4已退款 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 退款时间 */
    private LocalDateTime refundTime;

    /** 订单超时时间 */
    private LocalDateTime expireTime;

    // ========== 快照信息 ==========

    private String snapshotConcertName;
    private String snapshotArtistName;
    private String snapshotAreaName;
    private String snapshotTierName;
    private BigDecimal snapshotTicketPrice;
    private String snapshotVenueName;
    private String snapshotCity;
    private String snapshotCoverUrl;
    private LocalDateTime snapshotConcertTime;
}