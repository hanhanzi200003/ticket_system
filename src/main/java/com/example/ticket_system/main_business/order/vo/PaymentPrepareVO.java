package com.example.ticket_system.main_business.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付准备返回值
 * <p>
 * 调用 preparePay 后返回的支付参数，前端据此发起支付请求。
 */
@Data
public class PaymentPrepareVO {
    /** 订单ID */
    private Long orderId;
    /** 订单号 */
    private String orderNo;
    /** 订单金额 */
    private BigDecimal amount;
    /** 订单描述 */
    private String description;
    /** 订单超时时间 */
    private LocalDateTime expireTime;
    /** 支付会话令牌（防重复支付） */
    private String prepayToken;
    /** 当前订单状态：1=支付中 */
    private Integer status;

    // ===== 支付网关参数 =====

    /** 支付链接（前端跳转用） */
    private String payUrl;

    /** 预支付 ID（如微信 prepay_id） */
    private String prepayId;

    /** 支付平台交易号（支付成功时） */
    private String transactionId;

    /** 额外参数（各平台特有参数） */
    private Map<String, Object> extraParams;
}