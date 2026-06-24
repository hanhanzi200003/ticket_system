package com.example.ticket_system.main_business.pay.gateway;

import java.util.Map;

/**
 * 支付网关接口（策略模式）
 * <p>
 * 所有支付平台（Mock / 微信 / 支付宝等）必须实现此接口。
 * 业务层通过 {@link PaymentGatewayFactory} 获取当前激活的网关，完全解耦。
 */
public interface PaymentGateway {

    /**
     * 发起支付
     * <p>
     * 返回支付参数（支付链接、prepay_id 等），前端据此拉起收银台。
     */
    PayResponse pay(PayRequest request);

    /**
     * 处理支付回调（网关异步通知）
     * <p>
     * 验签 → 解析结果 → 返回统一格式供业务层更新订单状态。
     */
    PayCallbackResult handlePayCallback(Map<String, String> callbackParams);

    /**
     * 发起退款
     */
    RefundResponse refund(RefundRequest request);

    /**
     * 查询支付状态
     */
    PayResponse queryPayStatus(String orderNo);

    /**
     * 查询退款状态
     */
    RefundResponse queryRefundStatus(String orderNo);

    /**
     * 支付渠道标识：mock / wechat / alipay
     */
    String getChannel();
}