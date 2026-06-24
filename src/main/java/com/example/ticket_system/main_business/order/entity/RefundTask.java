package com.example.ticket_system.main_business.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款任务表
 */
@Data
@TableName(value = "refund_task", keepGlobalPrefix = true)
public class RefundTask {

    // ==================== 状态常量 ====================

    /** 待退款 */
    public static final int STATUS_PENDING = 0;

    /** 退款中 */
    public static final int STATUS_PROCESSING = 1;

    /** 成功 */
    public static final int STATUS_SUCCESS = 2;

    /** 失败 */
    public static final int STATUS_FAILED = 3;

    // ==================== 退款原因常量 ====================

    /** 用户退款 */
    public static final int REFUND_TYPE_USER = 1;

    /** 演唱会取消 */
    public static final int REFUND_TYPE_CONCERT_CANCEL = 2;

    /** 管理员强制退款 */
    public static final int REFUND_TYPE_ADMIN = 3;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 退款任务号（幂等核心）
     */
    @TableField("refund_no")
    private String refundNo;

    /**
     * 订单ID
     */
    @TableField("order_id")
    private Long orderId;

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
     * 原支付流水号
     */
    @TableField("pay_transaction_id")
    private String payTransactionId;

    /**
     * 退款金额
     */
    @TableField("refund_amount")
    private BigDecimal refundAmount;

    /**
     * 退款原因
     * 1 用户退款
     * 2 演唱会取消
     * 3 管理员强制退款
     */
    @TableField("refund_type")
    private Integer refundType;

    /**
     * 状态
     * 0 待退款
     * 1 退款中
     * 2 成功
     * 3 失败
     */
    @TableField("status")
    private Integer status;

    /**
     * 重试次数
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    @TableField("max_retry")
    private Integer maxRetry;

    /**
     * 下次重试时间
     */
    @TableField("next_retry_time")
    private LocalDateTime nextRetryTime;

    /**
     * 第三方退款流水号
     */
    @TableField("refund_transaction_id")
    private String refundTransactionId;

    /**
     * 失败原因
     */
    @TableField("fail_reason")
    private String failReason;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}