package com.example.ticket_system.main_business.pay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟支付请求
 * <p>
 * 模拟外部支付平台，调用方通过 mockResult 选择支付成功或失败
 */
@Data
public class MockPayRequest {

    /** 业务订单号 */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 支付金额 */
    @NotNull(message = "支付金额不能为空")
    @Positive(message = "支付金额必须大于0")
    private BigDecimal amount;

    /** 模拟支付结果：SUCCESS / FAIL */
    @NotBlank(message = "模拟结果不能为空")
    private String mockResult;

    /** 支付描述（可选） */
    private String description;
}