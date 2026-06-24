package com.example.ticket_system.main_business.pay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟支付响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockPayResponse {

    /** 支付状态：PAYING（支付中） / SUCCESS（成功） / FAIL（失败） */
    private String status;

    /** 是否最终成功（status=SUCCESS 时为 true） */
    private boolean success;

    /** 模拟交易号（支付成功时有值） */
    private String transactionId;

    /** 支付金额 */
    private BigDecimal amount;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 提示信息 */
    private String message;
}