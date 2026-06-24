package com.example.ticket_system.main_business.pay.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 通用支付请求
 * <p>
 * 业务层组装，传入支付网关发起支付。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayRequest {

    /** 业务订单号 */
    private String orderNo;

    /** 支付金额 */
    private BigDecimal amount;

    /** 支付描述 */
    private String description;

    /** 订单超时时间 */
    private LocalDateTime expireTime;

    /** 回调通知地址（网关异步通知） */
    private String notifyUrl;
}