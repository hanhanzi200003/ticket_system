package com.example.ticket_system.main_business.pay.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 通用退款响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {

    /** 是否成功发起退款 */
    private boolean success;

    /** 退款状态：SUCCESS / REFUNDING / FAIL */
    private String status;

    /** 提示信息 */
    private String message;

    /** 退款交易号 */
    private String refundTransactionId;

    /** 退款金额 */
    private BigDecimal refundAmount;

    /** 退款时间 */
    private LocalDateTime refundTime;
}