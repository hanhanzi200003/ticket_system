package com.example.ticket_system.main_business.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单信息实体类
 */
@Data
@TableName("order_info")
public class OrderInfo {
    
    /**
     * 订单ID（雪花算法，内部使用）
     */
    @TableId(value = "order_id", type = IdType.ASSIGN_ID)
    private Long orderId;
    
    /**
     * 订单号（对外展示，唯一）
     */
    @TableField("order_no")
    private String orderNo;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 演唱会ID
     */
    @TableField("concert_id")
    private Long concertId;
    
    /**
     * 票档ID
     */
    @TableField("tier_id")
    private Long tierId;
    
    /**
     * 优惠券ID（可选）
     */
    @TableField("coupon_id")
    private Long couponId;
    
    /**
     * 城市
     */
    @TableField("city")
    private String city;
    
    /**
     * 购买数量
     */
    @TableField("quantity")
    private Integer quantity;
    
    /**
     * 座位信息JSON数组
     * 格式：[{"seat":"A区1排1座","price":680},{"seat":"A区1排2座","price":680}]
     */
    @TableField("seats_json")
    private String seatsJson;
    
    /**
     * 订单原价
     */
    @TableField("original_amount")
    private BigDecimal originalAmount;
    
    /**
     * 订单实付金额
     */
    @TableField("actual_amount")
    private BigDecimal actualAmount;
    
    /**
     * 订单状态：0待支付（已创建+锁座） 1支付中（已进入支付准备） 2已支付 3已取消 4已退款
     */
    @TableField("status")
    private Integer status;
    
    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
    
    /**
     * 支付时间
     */
    @TableField("pay_time")
    private LocalDateTime payTime;
    
    /**
     * 取消时间
     */
    @TableField("cancel_time")
    private LocalDateTime cancelTime;
    
    /**
     * 退款时间
     */
    @TableField("refund_time")
    private LocalDateTime refundTime;
    
    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
    
    /**
     * 订单超时时间
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;
    
    /**
     * 用户删除标记：0未删除 1已删除（隐藏）
     */
    @TableField("user_deleted")
    private Integer userDeleted;

    /**
     * 支付流水号（第三方支付平台返回）
     */
    @TableField("pay_transaction_id")
    private String payTransactionId;
    
    // ========== 快照字段 ==========
    
    /**
     * 快照-演唱会名称
     */
    @TableField("snapshot_concert_name")
    private String snapshotConcertName;
    
    /**
     * 快照-艺人名称
     */
    @TableField("snapshot_artist_name")
    private String snapshotArtistName;
    
    /**
     * 快照-座位区域名称
     */
    @TableField("snapshot_area_name")
    private String snapshotAreaName;
    
    /**
     * 快照-票单价
     */
    @TableField("snapshot_ticket_price")
    private BigDecimal snapshotTicketPrice;
    
    /**
     * 快照-场馆名称
     */
    @TableField("snapshot_venue_name")
    private String snapshotVenueName;
    
    /**
     * 快照-城市
     */
    @TableField("snapshot_city")
    private String snapshotCity;

    /**
     * 快照-票档名称
     */
    @TableField("snapshot_tier_name")
    private String snapshotTierName;

    /**
     * 快照-封面图URL
     */
    @TableField("snapshot_cover_url")
    private String snapshotCoverUrl;

    /**
     * 快照-演出时间
     */
    @TableField("snapshot_concert_time")
    private LocalDateTime snapshotConcertTime;
}
