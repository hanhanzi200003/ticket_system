package com.example.ticket_system.main_business.order.service;

import com.example.ticket_system.main_business.order.dto.CreateOrderDTO;
import com.example.ticket_system.main_business.order.dto.OrderQueryDTO;
import com.example.ticket_system.main_business.order.vo.OrderPageVO;
import com.example.ticket_system.main_business.order.vo.OrderVO;
import com.example.ticket_system.main_business.order.vo.PaymentPrepareVO;
import com.example.ticket_system.main_business.pay.gateway.PayCallbackResult;

/**
 * 订单服务接口
 */
public interface OrderService {

    /**
     * 创建订单
     *
     * @param userId 用户ID
     * @param dto    创建订单请求
     * @return 订单信息
     */
    OrderVO createOrder(Long userId, CreateOrderDTO dto);

    /**
     * 取消订单
     * <p>
     * 创建取消任务（TASK_TYPE_USER_CANCEL），异步执行取消逻辑。
     * 由 CancelTaskConsumer（MQ）或 CancelTaskScanner（定时）执行。
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 任务编号（taskNo），可用于查询任务状态
     */
    String cancelOrder(Long userId, Long orderId);

    /**
     * 支付准备（进入支付流程）
     * <p>
     * 1. 校验订单归属和状态（已支付/已取消/已退款直接拒绝）
     * 2. 校验订单是否超时
     * 3. 设置订单状态为"支付中"(1)，防止重复支付
     * 4. 调用支付网关发起支付，返回支付参数
     * <p>
     * 幂等：若订单已为"支付中"，直接返回支付参数
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 支付参数（包含支付链接/预付ID等）
     */
    PaymentPrepareVO preparePay(Long userId, Long orderId);

    /**
     * 处理支付回调（支付网关异步通知时调用）
     * <p>
     * 验签通过后，更新订单状态为"已支付"。
     * 幂等：已支付/已取消/已退款的订单跳过处理。
     *
     * @param callbackResult 支付网关回调处理结果
     */
    void processPayCallback(PayCallbackResult callbackResult);

    /**
     * 支付订单
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 订单信息
     */
    OrderVO payOrder(Long userId, Long orderId);

    /**
     * 查询订单详情
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @return 订单信息
     */
    OrderVO getOrderDetail(Long userId, Long orderId);

    /**
     * 内部超时取消 — 直接释放 Redis 座位 + 更新 DB，不走本地消息表
     * <p>
     * 调用方：MQ 延迟取消消费者 + OrderTimeoutScanner
     * 这两个链路本身已有重试/兜底机制，无需额外的消息表保障
     *
     * @param orderId 订单ID
     */
    void timeoutCancel(Long orderId);

    /**
     * 逻辑删除订单（对用户隐藏）
     * <p>
     * 仅能删除待支付(0) / 已取消(2) / 已退款(3) 的订单
     * 已支付(1) 的订单不可删除，否则用户无法核验票务
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     */
    void deleteOrder(Long userId, Long orderId);

    /**
     * 分页查询用户订单列表
     *
     * @param userId 用户ID
     * @param dto    查询条件
     * @return 分页订单列表
     */
    OrderPageVO listUserOrders(Long userId, OrderQueryDTO dto);
}