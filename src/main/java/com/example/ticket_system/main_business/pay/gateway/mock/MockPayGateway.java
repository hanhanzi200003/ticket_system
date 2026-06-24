package com.example.ticket_system.main_business.pay.gateway.mock;

import com.example.ticket_system.main_business.pay.dto.MockPayRequest;
import com.example.ticket_system.main_business.pay.dto.MockPayResponse;
import com.example.ticket_system.main_business.pay.dto.MockRefundRequest;
import com.example.ticket_system.main_business.pay.dto.MockRefundResponse;
import com.example.ticket_system.main_business.pay.gateway.*;
import com.example.ticket_system.main_business.pay.service.MockPayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 模拟支付网关实现
 * <p>
 * 包装 {@link MockPayService}，适配 {@link PaymentGateway} 接口。
 * 模拟支付同步返回结果（内部立即完成处理），适合开发/测试环境。
 * 切换为真实支付平台时，新建 WechatPayGateway / AlipayGateway 实现即可。
 */
@Component
public class MockPayGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPayGateway.class);

    @Autowired
    private MockPayService mockPayService;

    @Override
    public PayResponse pay(PayRequest request) {
        // 1. 构造模拟支付请求
        MockPayRequest mockRequest = new MockPayRequest();
        mockRequest.setOrderNo(request.getOrderNo());
        mockRequest.setAmount(request.getAmount());
        mockRequest.setMockResult("SUCCESS"); // 模拟默认成功
        mockRequest.setDescription(request.getDescription());

        // 2. 调用模拟支付服务（同步返回结果）
        MockPayResponse mockResponse = mockPayService.pay(mockRequest);

        // 3. 转换为统一返回值
        boolean isSuccess = "SUCCESS".equals(mockResponse.getStatus());

        PayResponse response = PayResponse.builder()
                .success(isSuccess)
                .status(mockResponse.getStatus())
                .message(mockResponse.getMessage())
                .transactionId(mockResponse.getTransactionId())
                .amount(mockResponse.getAmount())
                .payTime(mockResponse.getPayTime())
                .payUrl("/mock-pay/pay") // 模拟支付请求路径
                .prepayId(mockResponse.getTransactionId())
                .build();

        log.info("MockPayGateway.pay() → orderNo={}, status={}, success={}",
                request.getOrderNo(), response.getStatus(), response.isSuccess());

        return response;
    }

    @Override
    public PayCallbackResult handlePayCallback(Map<String, String> callbackParams) {
        // 模拟网关回调：直接根据订单号查询支付状态
        String orderNo = callbackParams.get("orderNo");
        MockPayResponse mockResponse = mockPayService.getPaymentStatus(orderNo);

        boolean isSuccess = "SUCCESS".equals(mockResponse.getStatus());

        return PayCallbackResult.builder()
                .verified(true) // 模拟：验签永远通过
                .orderNo(orderNo)
                .transactionId(mockResponse.getTransactionId())
                .payResult(isSuccess ? "SUCCESS" : "FAIL")
                .amount(mockResponse.getAmount())
                .payTime(mockResponse.getPayTime())
                .rawParams(callbackParams.toString())
                .build();
    }

    @Override
    public RefundResponse refund(RefundRequest request) {
        MockRefundRequest mockRequest = new MockRefundRequest();
        mockRequest.setOrderNo(request.getOrderNo());
        mockRequest.setRefundNo(request.getRefundNo());
        mockRequest.setOriginalTransactionId(request.getOriginalTransactionId());
        mockRequest.setRefundAmount(request.getRefundAmount());
        mockRequest.setMockResult("SUCCESS");
        mockRequest.setReason(request.getReason());

        MockRefundResponse mockResponse = mockPayService.refund(mockRequest);

        boolean isSuccess = "SUCCESS".equals(mockResponse.getStatus());

        return RefundResponse.builder()
                .success(isSuccess)
                .status(mockResponse.getStatus())
                .message(mockResponse.getMessage())
                .refundTransactionId(mockResponse.getRefundTransactionId())
                .refundAmount(mockResponse.getRefundAmount())
                .refundTime(mockResponse.getRefundTime())
                .build();
    }

    @Override
    public PayResponse queryPayStatus(String orderNo) {
        MockPayResponse mockResponse = mockPayService.getPaymentStatus(orderNo);

        boolean isSuccess = "SUCCESS".equals(mockResponse.getStatus());

        return PayResponse.builder()
                .success(isSuccess)
                .status("NOT_FOUND".equals(mockResponse.getStatus()) ? "NOT_FOUND" : mockResponse.getStatus())
                .message(mockResponse.getMessage())
                .transactionId(mockResponse.getTransactionId())
                .amount(mockResponse.getAmount())
                .payTime(mockResponse.getPayTime())
                .build();
    }

    @Override
    public RefundResponse queryRefundStatus(String refundNo) {
        MockRefundResponse mockResponse = mockPayService.getRefundStatus(refundNo);

        boolean isSuccess = "SUCCESS".equals(mockResponse.getStatus());

        return RefundResponse.builder()
                .success(isSuccess)
                .status("NOT_FOUND".equals(mockResponse.getStatus()) ? "NOT_FOUND" : mockResponse.getStatus())
                .message(mockResponse.getMessage())
                .refundTransactionId(mockResponse.getRefundTransactionId())
                .refundAmount(mockResponse.getRefundAmount())
                .refundTime(mockResponse.getRefundTime())
                .build();
    }

    @Override
    public String getChannel() {
        return "mock";
    }
}