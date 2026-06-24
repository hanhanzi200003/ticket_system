package com.example.ticket_system.main_business.pay.controller;

import com.example.ticket_system.config.utils.Result;
import com.example.ticket_system.main_business.order.service.OrderService;
import com.example.ticket_system.main_business.pay.gateway.PayCallbackResult;
import com.example.ticket_system.main_business.pay.gateway.PaymentGatewayFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一支付回调控制器
 * <p>
 * 接收支付平台异步通知，验签后更新订单状态。
 * 切换支付平台时无需修改此控制器，网关实现自动适配。
 */
@RestController
@RequestMapping("/pay")
public class PayCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PayCallbackController.class);

    @Autowired
    private PaymentGatewayFactory paymentGatewayFactory;

    @Autowired
    private OrderService orderService;

    /**
     * 支付回调入口（接收支付平台的异步通知）
     * <p>
     * 流程：网关通知 → 验签 → 处理回调 → 更新订单状态
     * 同一通知可能重复发送，业务层有幂等处理。
     */
    @PostMapping("/callback")
    public Result<String> handlePayCallback(HttpServletRequest request) {
        // 1. 收集回调参数
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });

        log.info("收到支付回调通知：params={}", params);

        // 2. 网关验签 + 解析
        PayCallbackResult callbackResult;
        try {
            callbackResult = paymentGatewayFactory.getGateway().handlePayCallback(params);
        } catch (Exception e) {
            log.error("支付回调处理异常", e);
            return Result.error("回调处理失败");
        }

        if (!callbackResult.isVerified()) {
            log.warn("支付回调验签失败：orderNo={}", callbackResult.getOrderNo());
            return Result.error("验签失败");
        }

        // 3. 业务处理（幂等）
        orderService.processPayCallback(callbackResult);

        log.info("支付回调处理完成：orderNo={}, result={}",
                callbackResult.getOrderNo(), callbackResult.getPayResult());

        // 4. 返回成功响应（支付平台期望的格式）
        return Result.success("处理成功");
    }

    /**
     * 手动触发回调（模拟支付使用）
     * <p>
     * 模拟支付场景下，前端调起 mock-pay 支付成功后，调用此接口通知后端完成订单。
     */
    @PostMapping("/callback/manual")
    public Result<String> manualPayCallback(String orderNo) {
        log.info("收到手动支付回调：orderNo={}", orderNo);

        // 构造回调参数供网关解析
        Map<String, String> params = new HashMap<>();
        params.put("orderNo", orderNo);

        PayCallbackResult callbackResult = paymentGatewayFactory.getGateway().handlePayCallback(params);
        if (!callbackResult.isVerified()) {
            return Result.error("支付状态异常");
        }

        orderService.processPayCallback(callbackResult);

        return Result.success("支付确认成功");
    }
}