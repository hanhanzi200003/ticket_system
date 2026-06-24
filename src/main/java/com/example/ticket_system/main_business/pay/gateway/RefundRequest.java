package com.example.ticket_system.main_business.pay.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 通用退款请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    /** 业务订单号 */
    private String orderNo;

    /** 退款任务号（幂等键，同一个 refundNo 不会重复退款） */
    private String refundNo;

    /** 原支付平台交易号 */
    private String originalTransactionId;

    /** 退款金额 */
    private BigDecimal refundAmount;

    /** 退款原因 */
    private String reason;
}