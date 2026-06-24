package com.example.ticket_system.main_business.pay.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 通用支付响应
 * <p>
 * 支付网关调用的统一返回值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayResponse {

    /** 是否成功发起支付 */
    private boolean success;

    /** 支付状态：SUCCESS / PAYING / FAIL */
    private String status;

    /** 提示信息 */
    private String message;

    /** 支付平台交易号（支付成功时有值） */
    private String transactionId;

    /** 支付金额 */
    private BigDecimal amount;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 支付链接（前端跳转用） */
    private String payUrl;

    /** 预支付 ID（如微信 prepay_id） */
    private String prepayId;

    /** 额外参数（各平台特有的支付参数） */
    private Map<String, Object> extraParams;
}