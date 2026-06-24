package com.example.ticket_system.main_business.pay.service;

import com.example.ticket_system.main_business.pay.dto.MockPayRequest;
import com.example.ticket_system.main_business.pay.dto.MockPayResponse;
import com.example.ticket_system.main_business.pay.dto.MockRefundRequest;
import com.example.ticket_system.main_business.pay.dto.MockRefundResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟支付服务
 * <p>
 * 纯后端内存实现（无 DB / Redis 依赖）：
 * <ul>
 *   <li><b>幂等校验</b> — 同一订单号重复请求返回已缓存的结果</li>
 *   <li><b>状态流转</b> — PAYING(支付中) → SUCCESS(成功) / FAIL(失败)</li>
 * </ul>
 * 后续接入真实支付平台时，替换此实现即可。
 */
@Service
public class MockPayService {

    private static final Logger log = LoggerFactory.getLogger(MockPayService.class);

    /** 支付状态缓存（key = orderNo，幂等 + 状态查询） */
    private final ConcurrentHashMap<String, MockPayResponse> paymentCache = new ConcurrentHashMap<>();

    /** 退款状态缓存（key = refundNo，幂等 + 状态查询） */
    private final ConcurrentHashMap<String, MockRefundResponse> refundCache = new ConcurrentHashMap<>();

    // ==================== 支付 ====================

    /**
     * 模拟支付
     * <p>
     * 状态流转：PAYING(支付中) → SUCCESS(成功) / FAIL(失败)
     * 幂等：同一 orderNo 重复调用直接返回上一次的结果
     */
    public MockPayResponse pay(MockPayRequest request) {
        // 1. 幂等校验：已处理过的订单直接返回缓存
        MockPayResponse cached = paymentCache.get(request.getOrderNo());
        if (cached != null) {
            log.info("幂等命中，返回已缓存的支付结果：orderNo={}, status={}",
                    request.getOrderNo(), cached.getStatus());
            return cached;
        }

        // 2. 初始状态：支付中
        LocalDateTime now = LocalDateTime.now();
        MockPayResponse paying = MockPayResponse.builder()
                .status("PAYING")
                .success(false)
                .amount(request.getAmount())
                .payTime(now)
                .message(String.format("【支付中】订单 %s，金额 %.2f 元，正在处理中",
                        request.getOrderNo(), request.getAmount()))
                .build();
        // 先缓存 PAYING 状态（模拟已受理，防重复提交）
        paymentCache.put(request.getOrderNo(), paying);
        log.info("模拟支付受理：orderNo={}, status=PAYING", request.getOrderNo());

        // 3. 模拟支付处理（同步流转到最终状态）
        boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getMockResult());
        String transactionId = null;
        String message;

        if (isSuccess) {
            transactionId = generateTransactionId("P");
            message = String.format("【模拟支付成功】订单 %s，支付金额 %.2f 元，交易号 %s",
                    request.getOrderNo(), request.getAmount(), transactionId);
            log.info("模拟支付成功：{}", message);
        } else {
            message = String.format("【模拟支付失败】订单 %s，支付金额 %.2f 元，原因：模拟支付返回失败",
                    request.getOrderNo(), request.getAmount());
            log.warn("模拟支付失败：{}", message);
        }

        // 4. 更新为最终状态
        MockPayResponse finalResult = MockPayResponse.builder()
                .status(isSuccess ? "SUCCESS" : "FAIL")
                .success(isSuccess)
                .transactionId(transactionId)
                .amount(request.getAmount())
                .payTime(now)
                .message(message)
                .build();
        paymentCache.put(request.getOrderNo(), finalResult);
        log.info("支付状态流转完成：orderNo={}, {} → {}",
                request.getOrderNo(), "PAYING", finalResult.getStatus());

        return finalResult;
    }

    // ==================== 退款 ====================

    /**
     * 模拟退款
     * <p>
     * 状态流转：REFUNDING(退款中) → SUCCESS(成功) / FAIL(失败)
     * 幂等：同一 refundNo 重复调用直接返回上一次的结果
     */
    public MockRefundResponse refund(MockRefundRequest request) {
        // 幂等键：优先使用 refundNo，降级到 orderNo
        String idempotentKey = request.getRefundNo() != null ? request.getRefundNo() : request.getOrderNo();

        // 1. 幂等校验
        MockRefundResponse cached = refundCache.get(idempotentKey);
        if (cached != null) {
            log.info("幂等命中，返回已缓存的退款结果：key={}, status={}",
                    idempotentKey, cached.getStatus());
            return cached;
        }

        // 2. 初始状态：退款中
        LocalDateTime now = LocalDateTime.now();
        MockRefundResponse refunding = MockRefundResponse.builder()
                .status("REFUNDING")
                .success(false)
                .refundAmount(request.getRefundAmount())
                .refundTime(now)
                .message(String.format("【退款中】订单 %s，退款金额 %.2f 元，正在处理中",
                        request.getOrderNo(), request.getRefundAmount()))
                .build();
        refundCache.put(idempotentKey, refunding);
        log.info("模拟退款受理：key={}, orderNo={}, status=REFUNDING", idempotentKey, request.getOrderNo());

        // 3. 流转到最终状态
        boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getMockResult());
        String refundTransactionId = null;
        String message;

        if (isSuccess) {
            refundTransactionId = generateTransactionId("R");
            message = String.format("【模拟退款成功】订单 %s，退款金额 %.2f 元，原交易号 %s，退款交易号 %s",
                    request.getOrderNo(), request.getRefundAmount(),
                    request.getOriginalTransactionId(), refundTransactionId);
            log.info("模拟退款成功：{}", message);
        } else {
            message = String.format("【模拟退款失败】订单 %s，退款金额 %.2f 元，原交易号 %s，原因：模拟退款返回失败",
                    request.getOrderNo(), request.getRefundAmount(),
                    request.getOriginalTransactionId());
            log.warn("模拟退款失败：{}", message);
        }

        // 4. 更新为最终状态
        MockRefundResponse finalResult = MockRefundResponse.builder()
                .status(isSuccess ? "SUCCESS" : "FAIL")
                .success(isSuccess)
                .refundTransactionId(refundTransactionId)
                .refundAmount(request.getRefundAmount())
                .refundTime(now)
                .message(message)
                .build();
        refundCache.put(idempotentKey, finalResult);
        log.info("退款状态流转完成：key={}, {} → {}",
                idempotentKey, "REFUNDING", finalResult.getStatus());

        return finalResult;
    }

    // ==================== 状态查询 ====================

    /**
     * 查询支付状态
     *
     * @param orderNo 订单号
     * @return 支付状态响应（未找到时返回 null，status=NOT_FOUND）
     */
    public MockPayResponse getPaymentStatus(String orderNo) {
        MockPayResponse result = paymentCache.get(orderNo);
        if (result == null) {
            return MockPayResponse.builder()
                    .status("NOT_FOUND")
                    .success(false)
                    .message("订单 " + orderNo + " 无支付记录")
                    .build();
        }
        return result;
    }

    /**
     * 查询退款状态
     *
     * @param refundNo 退款任务号
     * @return 退款状态响应
     */
    public MockRefundResponse getRefundStatus(String refundNo) {
        MockRefundResponse result = refundCache.get(refundNo);
        if (result == null) {
            return MockRefundResponse.builder()
                    .status("NOT_FOUND")
                    .success(false)
                    .message("退款 " + refundNo + " 无退款记录")
                    .build();
        }
        return result;
    }

    // ==================== 工具方法 ====================

    /** 生成模拟交易号：前缀 + 时间戳 + 随机串 */
    private String generateTransactionId(String prefix) {
        return prefix + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /** 打印当前缓存状态（调试用） */
    @PostConstruct
    public void logInit() {
        log.info("MockPayService 已启动，支付/退款缓存为空，等待请求...");
    }

    /** 获取当前支付缓存快照（调试 / 管理用） */
    public ConcurrentHashMap<String, MockPayResponse> getPaymentCacheSnapshot() {
        return new ConcurrentHashMap<>(paymentCache);
    }

    /** 获取当前退款缓存快照（调试 / 管理用） */
    public ConcurrentHashMap<String, MockRefundResponse> getRefundCacheSnapshot() {
        return new ConcurrentHashMap<>(refundCache);
    }
}