package com.example.ticket_system.main_business.pay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟退款响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockRefundResponse {

    /** 退款状态：REFUNDING（退款中） / SUCCESS（成功） / FAIL（失败） */
    private String status;

    /** 是否最终成功（status=SUCCESS 时为 true） */
    private boolean success;

    /** 退款交易号（退款成功时有值） */
    private String refundTransactionId;

    /** 退款金额 */
    private BigDecimal refundAmount;

    /** 退款时间 */
    private LocalDateTime refundTime;

    /** 提示信息 */
    private String message;
}