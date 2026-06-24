package com.example.ticket_system.main_business.pay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟退款请求
 */
@Data
public class MockRefundRequest {

    /** 业务订单号 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 退款任务号（幂等键） */
    private String refundNo;

    /** 原支付交易号 */
    @NotBlank(message = "原交易号不能为空")
    private String originalTransactionId;

    /** 退款金额 */
    @NotNull(message = "退款金额不能为空")
    @Positive(message = "退款金额必须大于0")
    private BigDecimal refundAmount;

    /** 模拟退款结果：SUCCESS / FAIL */
    @NotBlank(message = "模拟结果不能为空")
    private String mockResult;

    /** 退款原因（可选） */
    private String reason;
}