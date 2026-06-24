package com.example.ticket_system.main_business.pay.controller;

import com.example.ticket_system.config.utils.Result;
import com.example.ticket_system.main_business.pay.dto.MockPayRequest;
import com.example.ticket_system.main_business.pay.dto.MockPayResponse;
import com.example.ticket_system.main_business.pay.dto.MockRefundRequest;
import com.example.ticket_system.main_business.pay.dto.MockRefundResponse;
import com.example.ticket_system.main_business.pay.service.MockPayService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 模拟支付控制器
 * <p>
 * 模拟外部支付平台的 REST API，后续对接真实支付平台时直接替换 base-url 即可。
 * 调用方通过 mockResult=SUCCESS/FAIL 控制支付/退款结果。
 */
@RestController
@RequestMapping("/mock-pay")
public class MockPayController {

    private static final Logger log = LoggerFactory.getLogger(MockPayController.class);

    @Autowired
    private MockPayService mockPayService;

    /**
     * 模拟支付
     * <p>
     * 幂等：同一 orderNo 重复请求返回已缓存的结果
     * 状态流转：PAYING(支付中) → SUCCESS(成功) / FAIL(失败)
     * <p>
     * 请求示例：
     * <pre>
     * POST /mock-pay/pay
     * {
     *   "orderNo": "ORDER20250615001",
     *   "amount": 599.00,
     *   "mockResult": "SUCCESS",
     *   "description": "购买演唱会门票"
     * }
     * </pre>
     */
    @PostMapping("/pay")
    public Result<MockPayResponse> pay(@Valid @RequestBody MockPayRequest request) {
        log.info("收到模拟支付请求：orderNo={}, amount={}, mockResult={}",
                request.getOrderNo(), request.getAmount(), request.getMockResult());

        MockPayResponse response = mockPayService.pay(request);

        if ("SUCCESS".equals(response.getStatus())) {
            return new Result<>(200, response.getMessage(), response);
        } else if ("PAYING".equals(response.getStatus())) {
            return new Result<>(202, response.getMessage(), response);
        } else {
            return new Result<>(400, response.getMessage(), response);
        }
    }

    /**
     * 模拟退款
     * <p>
     * 幂等：同一 orderNo 重复请求返回已缓存的结果
     * 状态流转：REFUNDING(退款中) → SUCCESS(成功) / FAIL(失败)
     * <p>
     * 请求示例：
     * <pre>
     * POST /mock-pay/refund
     * {
     *   "orderNo": "ORDER20250615001",
     *   "originalTransactionId": "P20250615001...",
     *   "refundAmount": 599.00,
     *   "mockResult": "SUCCESS",
     *   "reason": "用户申请退款"
     * }
     * </pre>
     */
    @PostMapping("/refund")
    public Result<MockRefundResponse> refund(@Valid @RequestBody MockRefundRequest request) {
        log.info("收到模拟退款请求：orderNo={}, refundAmount={}, mockResult={}",
                request.getOrderNo(), request.getRefundAmount(), request.getMockResult());

        MockRefundResponse response = mockPayService.refund(request);

        if ("SUCCESS".equals(response.getStatus())) {
            return new Result<>(200, response.getMessage(), response);
        } else if ("REFUNDING".equals(response.getStatus())) {
            return new Result<>(202, response.getMessage(), response);
        } else {
            return new Result<>(400, response.getMessage(), response);
        }
    }

    /**
     * 查询支付状态
     * <p>
     * 请求示例：GET /mock-pay/pay/status/ORDER20250615001
     *
     * @param orderNo 订单号
     * @return 当前支付状态（PAYING / SUCCESS / FAIL / NOT_FOUND）
     */
    @GetMapping("/pay/status/{orderNo}")
    public Result<MockPayResponse> getPaymentStatus(@PathVariable String orderNo) {
        MockPayResponse response = mockPayService.getPaymentStatus(orderNo);
        if ("NOT_FOUND".equals(response.getStatus())) {
            return new Result<>(404, response.getMessage(), response);
        }
        return new Result<>(200, response.getMessage(), response);
    }

    /**
     * 查询退款状态
     * <p>
     * 请求示例：GET /mock-pay/refund/status/ORDER20250615001
     *
     * @param orderNo 订单号
     * @return 当前退款状态（REFUNDING / SUCCESS / FAIL / NOT_FOUND）
     */
    @GetMapping("/refund/status/{orderNo}")
    public Result<MockRefundResponse> getRefundStatus(@PathVariable String orderNo) {
        MockRefundResponse response = mockPayService.getRefundStatus(orderNo);
        if ("NOT_FOUND".equals(response.getStatus())) {
            return new Result<>(404, response.getMessage(), response);
        }
        return new Result<>(200, response.getMessage(), response);
    }
}