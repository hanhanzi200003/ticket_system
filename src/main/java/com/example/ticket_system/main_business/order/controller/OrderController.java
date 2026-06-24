package com.example.ticket_system.main_business.order.controller;

import com.example.ticket_system.config.utils.Result;
import com.example.ticket_system.main_business.order.dto.CancelOrderDTO;
import com.example.ticket_system.main_business.order.dto.CreateOrderDTO;
import com.example.ticket_system.main_business.order.dto.DeleteOrderDTO;
import com.example.ticket_system.main_business.order.dto.OrderQueryDTO;
import com.example.ticket_system.main_business.order.dto.PayOrderDTO;
import com.example.ticket_system.main_business.order.dto.PreparePayDTO;
import com.example.ticket_system.main_business.order.service.OrderService;
import com.example.ticket_system.main_business.order.vo.OrderPageVO;
import com.example.ticket_system.main_business.order.vo.OrderVO;
import com.example.ticket_system.main_business.order.vo.PaymentPrepareVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端订单控制器（需登录）
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 创建订单
     *
     * @param dto 创建订单请求
     * @return 订单信息
     */
    @PostMapping("/create")
    public Result<OrderVO> createOrder(@Valid @RequestBody CreateOrderDTO dto,
                                       HttpServletRequest request) {
        Long userId = getUserId(request);
        OrderVO order = orderService.createOrder(userId, dto);
        return Result.success("下单成功", order);
    }

    /**
     * 取消订单（异步 — 创建取消任务）
     * <p>
     * 创建取消任务后立即返回任务编号，实际的取消逻辑由
     * CancelTaskConsumer（MQ）或 CancelTaskScanner（定时）异步执行。
     *
     * @param dto 取消订单请求
     * @return 任务编号
     */
    @PostMapping("/cancel")
    public Result<String> cancelOrder(@Valid @RequestBody CancelOrderDTO dto,
                                       HttpServletRequest request) {
        Long userId = getUserId(request);
        String taskNo = orderService.cancelOrder(userId, dto.getOrderId());
        return Result.success("取消请求已提交，正在处理中", taskNo);
    }

    /**
     * 逻辑删除订单（对用户隐藏）
     * <p>
     * 已支付的订单不可删除，防止用户无法核验票务
     *
     * @param dto 删除订单请求
     * @return 操作结果
     */
    @PostMapping("/delete")
    public Result<Void> deleteOrder(@Valid @RequestBody DeleteOrderDTO dto,
                                    HttpServletRequest request) {
        Long userId = getUserId(request);
        orderService.deleteOrder(userId, dto.getOrderId());
        return Result.success("删除成功");
    }

    /**
     * 支付准备（进入支付流程）
     * <p>
     * 1. 校验订单归属和状态
     * 2. 设置订单为"支付中"状态，防止重复支付
     * 3. 返回支付参数
     *
     * @param dto 支付准备请求
     * @return 支付参数
     */
    @PostMapping("/prepare-pay")
    public Result<PaymentPrepareVO> preparePay(@Valid @RequestBody PreparePayDTO dto,
                                                HttpServletRequest request) {
        Long userId = getUserId(request);
        PaymentPrepareVO paymentInfo = orderService.preparePay(userId, dto.getOrderId());
        return Result.success("获取支付参数成功", paymentInfo);
    }

    /**
     * 支付订单
     *
     * @param dto 支付请求
     * @return 订单信息
     */
    @PostMapping("/pay")
    public Result<OrderVO> payOrder(@Valid @RequestBody PayOrderDTO dto,
                                    HttpServletRequest request) {
        Long userId = getUserId(request);
        OrderVO order = orderService.payOrder(userId, dto.getOrderId());
        return Result.success("支付成功", order);
    }

    /**
     * 查询订单详情
     *
     * @param orderId 订单ID
     * @return 订单信息
     */
    @GetMapping("/{orderId}")
    public Result<OrderVO> getOrderDetail(@PathVariable Long orderId,
                                          HttpServletRequest request) {
        Long userId = getUserId(request);
        OrderVO order = orderService.getOrderDetail(userId, orderId);
        return Result.success("查询成功", order);
    }

    /**
     * 分页查询用户订单列表
     *
     * @param dto 查询条件
     * @return 分页订单列表
     */
    @GetMapping("/list")
    public Result<OrderPageVO> listOrders(OrderQueryDTO dto,
                                          HttpServletRequest request) {
        Long userId = getUserId(request);
        // 参数安全校验
        if (dto.getPageNum() == null || dto.getPageNum() < 1) {
            dto.setPageNum(1);
        }
        if (dto.getPageSize() == null || dto.getPageSize() < 1) {
            dto.setPageSize(10);
        }
        if (dto.getPageSize() > 50) {
            dto.setPageSize(50);
        }
        OrderPageVO page = orderService.listUserOrders(userId, dto);
        return Result.success("查询成功", page);
    }

    /**
     * 从请求中获取当前用户ID
     */
    private Long getUserId(HttpServletRequest request) {
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr == null) {
            throw new com.example.ticket_system.config.exception.AllException(401, "未登录或登录已过期");
        }
        return (Long) userIdAttr;
    }
}