package com.example.ticket_system.main_business.pay.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付回调处理结果
 * <p>
 * 支付网关异步通知验签通过后，返回此结果供业务层更新订单状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayCallbackResult {

    /** 验签是否通过 */
    private boolean verified;

    /** 业务订单号 */
    private String orderNo;

    /** 支付平台交易号 */
    private String transactionId;

    /** 支付结果：SUCCESS / FAIL */
    private String payResult;

    /** 实际支付金额 */
    private java.math.BigDecimal amount;

    /** 支付时间 */
    private java.time.LocalDateTime payTime;

    /** 原始回调参数（存档用） */
    private String rawParams;
}