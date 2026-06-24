package com.example.ticket_system.main_business.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ticket_system.config.exception.AllException;
import com.example.ticket_system.config.utils.SnowflakeIdGenerator;
import com.example.ticket_system.main_business.event.entity.Artist;
import com.example.ticket_system.main_business.event.entity.Concert;
import com.example.ticket_system.main_business.event.entity.TicketTier;
import com.example.ticket_system.main_business.event.mapper.ArtistMapper;
import com.example.ticket_system.main_business.event.mapper.ConcertMapper;
import com.example.ticket_system.main_business.event.mapper.TicketTierMapper;
import com.example.ticket_system.main_business.order.dto.CreateOrderDTO;
import com.example.ticket_system.main_business.order.vo.PaymentPrepareVO;
import com.example.ticket_system.main_business.order.dto.OrderQueryDTO;
import com.example.ticket_system.main_business.order.entity.OrderCancelTask;
import com.example.ticket_system.main_business.order.entity.OrderInfo;
import com.example.ticket_system.main_business.order.mapper.OrderMapper;
import com.example.ticket_system.main_business.order.mq.OrderMQProducer;
import com.example.ticket_system.main_business.order.mq.OrderPersistMessage;
import com.example.ticket_system.main_business.order.service.OrderService;
import com.example.ticket_system.main_business.order.service.SeatInfoService;
import com.example.ticket_system.main_business.order.service.SeatService;
import com.example.ticket_system.main_business.order.vo.OrderPageVO;
import com.example.ticket_system.main_business.order.vo.OrderVO;
import com.example.ticket_system.main_business.order.vo.SeatLockResultVO;
import com.example.ticket_system.main_business.order.service.OrderCancelTaskService;
import com.example.ticket_system.main_business.pay.gateway.PayCallbackResult;
import com.example.ticket_system.main_business.pay.gateway.PayRequest;
import com.example.ticket_system.main_business.pay.gateway.PayResponse;
import com.example.ticket_system.main_business.pay.gateway.PaymentGatewayFactory;
import com.example.ticket_system.transactional_outbox.service.MqMessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 *
 * 核心设计：
 *   - Redis 操作（锁座/释放座位）同步执行（快速路径）
 *   - MySQL 操作通过 MQ 消息异步落库（慢速路径）
 *   - MQ 延迟消息 + 本地定时任务双保险处理超时订单
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ConcertMapper concertMapper;

    @Autowired
    private TicketTierMapper ticketTierMapper;

    @Autowired
    private ArtistMapper artistMapper;

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatInfoService seatInfoService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MqMessageService mqMessageService;

    @Autowired
    private OrderCancelTaskService cancelTaskService;

    @Autowired
    private OrderMQProducer orderMQProducer;

    @Autowired
    private PaymentGatewayFactory paymentGatewayFactory;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    /** 订单待支付超时时间（分钟）：下单后需在10分钟内完成支付 */
    private static final long ORDER_EXPIRE_MINUTES = 10;

    @Override
    public OrderVO createOrder(Long userId, CreateOrderDTO dto) {
        // ===================== 0. 防重复下单：同一用户全局只能有一个待支付订单 =====================
        Long existingPendingCount = orderMapper.selectCount(
                new QueryWrapper<OrderInfo>()
                        .eq("user_id", userId)
                        .in("status", 0, 1) // 待支付(0) 或 支付中(1)
                        .eq("user_deleted", 0));
        if (existingPendingCount != null && existingPendingCount > 0) {
            throw new AllException(400, "您有待支付的订单，请先完成支付或取消后再下单");
        }

        // ===================== 1. 校验（MySQL只读） =====================
        Concert concert = concertMapper.selectById(dto.getConcertId());
        if (concert == null) {
            throw new AllException(404, "演唱会不存在");
        }
        if (concert.getDeleted() != null && concert.getDeleted() == 1) {
            throw new AllException(400, "演唱会已下架");
        }
        if (concert.getAuditStatus() != Concert.AUDIT_APPROVED) {
            throw new AllException(400, "演唱会尚未通过审核");
        }

        Integer concertStatus = concert.getLifecycleStatus();
        if (concertStatus == null || concertStatus != Concert.LIFECYCLE_SELLING) {
            throw new AllException(400, "演唱会当前不在售票期间");
        }

        LocalDateTime now = LocalDateTime.now();
        if (concert.getSaleStartTime() != null && now.isBefore(concert.getSaleStartTime())) {
            throw new AllException(400, "尚未开售");
        }
        if (concert.getSaleEndTime() != null && now.isAfter(concert.getSaleEndTime())) {
            throw new AllException(400, "售票已结束");
        }

        TicketTier tier = ticketTierMapper.selectById(dto.getTierId());
        if (tier == null || !tier.getConcertId().equals(dto.getConcertId())) {
            throw new AllException(404, "票档不存在");
        }

        if (concert.getMaxPurchaseQuantity() != null && dto.getQuantity() > concert.getMaxPurchaseQuantity()) {
            throw new AllException(400, "单次购买数量不能超过" + concert.getMaxPurchaseQuantity() + "张");
        }

        Artist artist = artistMapper.selectById(concert.getArtistId());

        // ===================== 2. 生成 ID 与订单号 =====================
        Long orderId = snowflakeIdGenerator.nextId();
        String orderNo = generateOrderNo();

        // ===================== 3. Redis锁座（同步 — 高并发关键路径） =====================
        SeatLockResultVO lockResult = seatService.lockSeats(
                dto.getConcertId(), dto.getTierId(), dto.getQuantity(), orderNo);
        if (!lockResult.isSuccess()) {
            // Redis 熔断 → 降级为数据库锁座（SELECT FOR UPDATE + UPDATE 0→1）
            if ("REDIS_DEGRADED".equals(lockResult.getErrorMsg())) {
                log.warn("[下单降级] Redis已熔断，走数据库锁座：concertId={}, tierId={}, orderNo={}",
                        dto.getConcertId(), dto.getTierId(), orderNo);
                lockResult = seatInfoService.lockSeatsFromDb(
                        dto.getConcertId(), dto.getTierId(), dto.getQuantity());
            }
            if (!lockResult.isSuccess()) {
                throw new AllException(400, lockResult.getErrorMsg());
            }
        }

        // ===================== 4. 构建订单实体 + 消息体 =====================
        BigDecimal price = tier.getPrice();
        List<Map<String, Object>> seatList = lockResult.getSeatLabels().stream().map(label -> {
            Map<String, Object> seatMap = new LinkedHashMap<>();
            seatMap.put("seat", label);
            seatMap.put("price", price);
            return seatMap;
        }).collect(Collectors.toList());
        String seatsJson = toJson(seatList);

        BigDecimal originalAmount = price.multiply(BigDecimal.valueOf(dto.getQuantity()));
        LocalDateTime expireTime = now.plusMinutes(ORDER_EXPIRE_MINUTES);

        // 订单实体
        OrderInfo order = new OrderInfo();
        order.setOrderId(orderId);
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setConcertId(dto.getConcertId());
        order.setTierId(dto.getTierId());
        order.setCouponId(dto.getCouponId());
        order.setQuantity(dto.getQuantity());
        order.setSeatsJson(seatsJson);
        order.setOriginalAmount(originalAmount);
        order.setActualAmount(originalAmount);
        order.setStatus(0);
        order.setCreateTime(now);
        order.setExpireTime(expireTime);
        order.setUserDeleted(0);
        // 快照
        order.setSnapshotConcertName(concert.getName());
        order.setSnapshotArtistName(artist != null ? artist.getName() : "");
        order.setSnapshotAreaName(tier.getAreaName());
        order.setSnapshotTierName(tier.getAreaName());
        order.setSnapshotTicketPrice(price);
        order.setSnapshotVenueName(concert.getVenueName());
        order.setSnapshotCity(concert.getCity());
        order.setSnapshotCoverUrl(
                concert.getCoverThumbnail() != null ? concert.getCoverThumbnail() : concert.getCoverImage());
        order.setSnapshotConcertTime(concert.getStartTime());

        // 消息体（用于消费者同步 MySQL 库存）
        OrderPersistMessage persistMsg = new OrderPersistMessage();
        persistMsg.setOrderId(orderId);
        persistMsg.setOrderNo(orderNo);
        persistMsg.setUserId(userId);
        persistMsg.setConcertId(dto.getConcertId());
        persistMsg.setTierId(dto.getTierId());
        persistMsg.setCouponId(dto.getCouponId());
        persistMsg.setQuantity(dto.getQuantity());
        persistMsg.setSeatsJson(seatsJson);
        persistMsg.setTicketPrice(price);
        persistMsg.setOriginalAmount(originalAmount);
        persistMsg.setActualAmount(originalAmount);
        persistMsg.setExpireTime(expireTime);
        persistMsg.setSeatLabels(lockResult.getSeatLabels());
        persistMsg.setSnapshotConcertName(concert.getName());
        persistMsg.setSnapshotArtistName(artist != null ? artist.getName() : "");
        persistMsg.setSnapshotAreaName(tier.getAreaName());
        persistMsg.setSnapshotTierName(tier.getAreaName());
        persistMsg.setSnapshotVenueName(concert.getVenueName());
        persistMsg.setSnapshotCity(concert.getCity());
        persistMsg.setSnapshotCoverUrl(order.getSnapshotCoverUrl());
        persistMsg.setSnapshotConcertTime(concert.getStartTime());

        // ===================== 5. 事务：写订单表 + 写本地消息表 =====================
        String persistMsgId = mqMessageService.insertOrderAndPersistMessage(order, persistMsg);

        // ===================== 6. 尝试发送 MQ（事务外） =====================
        mqMessageService.trySendAndMark(persistMsgId);

        // ===================== 7. 发送 MQ 延迟取消消息（10分钟超时） =====================
        try {
            orderMQProducer.sendDelayCancelMessage(orderNo, orderId);
        } catch (Exception e) {
            log.warn("发送延迟取消消息失败（不影响下单，兜底定时任务会处理）：orderNo={}", orderNo, e);
        }

        log.info("用户[{}]下单成功，订单号[{}]，演唱会[{}]，票档[{}]，数量[{}]，座位[{}]",
                userId, orderNo, concert.getName(), tier.getAreaName(), dto.getQuantity(), seatsJson);

        // ===================== 8. 返回 =====================
        return buildOrderVOResult(orderId, orderNo, dto.getConcertId(), dto.getTierId(),
                dto.getQuantity(), seatsJson, price, originalAmount, 0, now, expireTime,
                persistMsg.getSnapshotConcertName(), persistMsg.getSnapshotArtistName(),
                tier.getAreaName(), persistMsg.getSnapshotVenueName(),
                persistMsg.getSnapshotCity(), persistMsg.getSnapshotCoverUrl(),
                concert.getStartTime());
    }

    @Override
    public String cancelOrder(Long userId, Long orderId) {
        // 快速校验：订单是否存在、归属、状态是否可取消
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new AllException(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new AllException(403, "无权操作此订单");
        }

        Integer status = order.getStatus();
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw new AllException(400, "当前订单状态不允许取消（状态：" + getStatusDesc(status) + "）");
        }

        // 创建取消任务（TASK_TYPE_USER_CANCEL），异步执行
        String taskNo = cancelTaskService.createCancelTask(orderId, userId,
                OrderCancelTask.TASK_TYPE_USER_CANCEL);

        log.info("用户[{}]取消订单[{}]请求已提交，taskNo={}", userId, order.getOrderNo(), taskNo);

        return taskNo;
    }

    @Override
    public PaymentPrepareVO preparePay(Long userId, Long orderId) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new AllException(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new AllException(403, "无权操作此订单");
        }

        Integer status = order.getStatus();
        LocalDateTime now = LocalDateTime.now();

        // 幂等：已支付拒绝
        if (status == 2) {
            throw new AllException(400, "该订单已支付，请勿重复支付");
        }
        // 已取消/已退款拒绝
        if (status == 3 || status == 4) {
            throw new AllException(400, "该订单已取消或已退款，无法支付");
        }

        // 已处于支付中，直接返回支付参数（幂等）
        if (status == 1) {
            return buildPaymentPrepareResult(order, null);
        }

        // 待支付：校验超时
        if (status == 0) {
            if (order.getExpireTime() != null && now.isAfter(order.getExpireTime())) {
                // 超时：释放 Redis 座位 + 更新 SeatInfo + 更新 DB
                List<String> seatLabels = parseSeatLabels(order.getSeatsJson());
                if (!seatLabels.isEmpty()) {
                    seatInfoService.batchReleaseSeats(order.getConcertId(), order.getTierId(), seatLabels);
                }
                order.setStatus(3); // 已取消
                order.setUpdateTime(now);
                orderMapper.updateById(order);
                throw new AllException(400, "订单已超时，请重新下单");
            }

            // 设置状态为"支付中"
            order.setStatus(1);
            order.setUpdateTime(now);
            orderMapper.updateById(order);
        }

        log.info("用户[{}]进入支付准备：orderNo={}, orderId={}", userId, order.getOrderNo(), orderId);

        // 调用支付网关发起支付
        PayRequest payRequest = PayRequest.builder()
                .orderNo(order.getOrderNo())
                .amount(order.getActualAmount())
                .description(order.getSnapshotConcertName())
                .expireTime(order.getExpireTime())
                .build();
        PayResponse payResponse = paymentGatewayFactory.getGateway().pay(payRequest);

        // 构建返回参数
        return buildPaymentPrepareResult(order, payResponse);
    }

    @Override
    public void processPayCallback(PayCallbackResult callbackResult) {
        if (!callbackResult.isVerified()) {
            log.warn("支付回调验签失败：orderNo={}", callbackResult.getOrderNo());
            return;
        }

        OrderInfo order = orderMapper.selectByOrderNo(callbackResult.getOrderNo());
        if (order == null) {
            log.warn("支付回调：订单不存在 orderNo={}", callbackResult.getOrderNo());
            return;
        }

        // 幂等：已支付(2)/已取消(3)/已退款(4) 跳过
        if (order.getStatus() == 2) {
            log.info("支付回调：订单[{}]已支付，跳过处理", callbackResult.getOrderNo());
            return;
        }
        if (order.getStatus() == 3 || order.getStatus() == 4) {
            log.warn("支付回调：订单[{}]已取消/已退款，状态={}，跳过", callbackResult.getOrderNo(), order.getStatus());
            return;
        }

        // 仅支付中(1)可完成支付
        if (order.getStatus() != 1) {
            log.warn("支付回调：订单[{}]状态异常，status={}，需支付中(1)", callbackResult.getOrderNo(), order.getStatus());
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 校验是否超时
        if (order.getExpireTime() != null && now.isAfter(order.getExpireTime())) {
            // 超时：释放 Redis 座位 + 更新 SeatInfo + 标记取消
            List<String> seatLabels = parseSeatLabels(order.getSeatsJson());
            if (!seatLabels.isEmpty()) {
                seatInfoService.batchReleaseSeats(order.getConcertId(), order.getTierId(), seatLabels);
            }
            order.setStatus(3);
            order.setUpdateTime(now);
            orderMapper.updateById(order);
            log.warn("支付回调：订单[{}]已超时，标记为已取消", callbackResult.getOrderNo());
            return;
        }

        // 支付成功 → 更新订单
        order.setStatus(2);
        order.setPayTime(callbackResult.getPayTime() != null ? callbackResult.getPayTime() : now);
        order.setUpdateTime(now);
        orderMapper.updateById(order);

        // SeatInfo 已在下单 MQ 落库时标记为已售(1)，支付成功无需再更新

        log.info("支付回调成功：orderNo={}, transactionId={}, amount={}",
                callbackResult.getOrderNo(), callbackResult.getTransactionId(), callbackResult.getAmount());
    }

    @Override
    public OrderVO payOrder(Long userId, Long orderId) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new AllException(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new AllException(403, "无权操作此订单");
        }
        // 仅"支付中"(1)可以完成支付
        if (order.getStatus() != 1) {
            throw new AllException(400, "当前订单状态不允许支付（" + getStatusDesc(order.getStatus()) + "）");
        }

        LocalDateTime now = LocalDateTime.now();

        // 校验是否已超时
        if (order.getExpireTime() != null && now.isAfter(order.getExpireTime())) {
            // 超时：释放 Redis 座位 + 更新 SeatInfo + 直接更新 DB
            List<String> seatLabels = parseSeatLabels(order.getSeatsJson());
            if (!seatLabels.isEmpty()) {
                seatInfoService.batchReleaseSeats(order.getConcertId(), order.getTierId(), seatLabels);
            }

            order.setStatus(3);
            order.setUpdateTime(now);
            orderMapper.updateById(order);

            throw new AllException(400, "订单已超时，请重新下单");
        }

        // 支付成功：同步更新 MySQL
        order.setStatus(2);
        order.setPayTime(now);
        order.setUpdateTime(now);
        orderMapper.updateById(order);

        // SeatInfo 已在下单 MQ 落库时标记为已售(1)，支付成功无需再更新

        log.info("用户[{}]支付订单[{}]成功，金额[{}]",
                userId, order.getOrderNo(), order.getActualAmount());

        return toOrderVO(order);
    }

    @Override
    public OrderVO getOrderDetail(Long userId, Long orderId) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new AllException(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new AllException(403, "无权查看此订单");
        }
        // 用户已逻辑删除的订单，对用户不可见
        if (order.getUserDeleted() != null && order.getUserDeleted() == 1) {
            throw new AllException(404, "订单不存在");
        }
        return toOrderVO(order);
    }

    @Override
    public OrderPageVO listUserOrders(Long userId, OrderQueryDTO dto) {
        int pageNum = dto.getPageNum() != null ? Math.max(dto.getPageNum(), 1) : 1;
        int pageSize = dto.getPageSize() != null ? dto.getPageSize() : 10;
        if (pageSize > 50) {
            pageSize = 50;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }

        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("user_deleted", 0);

        if (dto.getStatus() != null) {
            queryWrapper.eq("status", dto.getStatus());
        }
        if (dto.getOrderNo() != null && !dto.getOrderNo().isEmpty()) {
            queryWrapper.eq("order_no", dto.getOrderNo());
        }
        if (dto.getConcertName() != null && !dto.getConcertName().isEmpty()) {
            queryWrapper.like("snapshot_concert_name", dto.getConcertName());
        }
        queryWrapper.orderByDesc("create_time");

        Page<OrderInfo> page = new Page<>(pageNum, pageSize);
        IPage<OrderInfo> result = orderMapper.selectPage(page, queryWrapper);

        List<OrderVO> orderVOs = result.getRecords().stream()
                .map(this::toOrderVO)
                .collect(Collectors.toList());

        OrderPageVO pageVO = new OrderPageVO();
        pageVO.setPageNum((int) result.getCurrent());
        pageVO.setPageSize((int) result.getSize());
        pageVO.setTotal(result.getTotal());
        pageVO.setPages((int) result.getPages());
        pageVO.setList(orderVOs);

        return pageVO;
    }

    @Override
    public void timeoutCancel(Long orderId) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("超时取消：订单不存在 orderId={}", orderId);
            return;
        }
        if (order.getStatus() != 0 && order.getStatus() != 1) {
            log.info("超时取消：订单已处理，跳过 orderNo={}, status={}", order.getOrderNo(), order.getStatus());
            return;
        }

        // 释放 Redis 座位 + 更新 SeatInfo 为可售(0)
        List<String> seatLabels = parseSeatLabels(order.getSeatsJson());
        if (!seatLabels.isEmpty()) {
            seatInfoService.batchReleaseSeats(order.getConcertId(), order.getTierId(), seatLabels);
        }

        // 直接更新 DB
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(3);
        order.setUpdateTime(now);
        orderMapper.updateById(order);

        log.info("超时取消完成：orderNo={}, orderId={}", order.getOrderNo(), orderId);
    }

    @Override
    public void deleteOrder(Long userId, Long orderId) {
        OrderInfo order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new AllException(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new AllException(403, "无权操作此订单");
        }

        // 已支付的订单不可删除（用户需核验票务）
        if (order.getStatus() == 2) {
            throw new AllException(400, "已支付的订单不可删除，请联系客服处理");
        }

        // 逻辑删除：仅标记 user_deleted = 1，不对用户展示
        LocalDateTime now = LocalDateTime.now();
        order.setUserDeleted(1);
        order.setUpdateTime(now);
        orderMapper.updateById(order);

        log.info("用户[{}]已删除订单[{}]（逻辑删除），status={}",
                userId, order.getOrderNo(), order.getStatus());
    }

    // ==================== 私有方法 ====================

    /**
     * 将 OrderInfo 实体转换为 OrderVO
     */
    private OrderVO toOrderVO(OrderInfo order) {
        if (order == null) {
            return null;
        }
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        return vo;
    }

    /**
     * 在没有 OrderInfo 实体时，构建 OrderVO 返回给前端
     */
    private OrderVO buildOrderVOResult(Long orderId, String orderNo,
                                       Long concertId, Long tierId, Integer quantity,
                                       String seatsJson, BigDecimal price, BigDecimal amount,
                                       Integer status, LocalDateTime createTime, LocalDateTime expireTime,
                                       String concertName, String artistName, String tierName,
                                       String venueName, String city, String coverUrl,
                                       LocalDateTime concertTime) {
        OrderVO vo = new OrderVO();
        vo.setOrderId(orderId);
        vo.setOrderNo(orderNo);
        vo.setConcertId(concertId);
        vo.setTierId(tierId);
        vo.setQuantity(quantity);
        vo.setSeatsJson(seatsJson);
        vo.setOriginalAmount(amount);
        vo.setActualAmount(amount);
        vo.setStatus(status);
        vo.setCreateTime(createTime);
        vo.setExpireTime(expireTime);
        vo.setSnapshotConcertName(concertName);
        vo.setSnapshotArtistName(artistName);
        vo.setSnapshotAreaName(tierName);
        vo.setSnapshotTierName(tierName);
        vo.setSnapshotVenueName(venueName);
        vo.setSnapshotCity(city);
        vo.setSnapshotCoverUrl(coverUrl);
        vo.setSnapshotConcertTime(concertTime);
        return vo;
    }

    /**
     * 生成订单号：yyyyMMddHHmmss + 6位随机数字
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = new Random().nextInt(900000) + 100000;
        return timestamp + random;
    }

    /**
     * 从 seatsJson 中解析出座位标签列表
     */
    private List<String> parseSeatLabels(String seatsJson) {
        if (seatsJson == null || seatsJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> seatList = objectMapper.readValue(seatsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            return seatList.stream()
                    .map(m -> (String) m.get("seat"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            log.warn("解析 seatsJson 失败: {}", seatsJson, e);
            return Collections.emptyList();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    private String getStatusDesc(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "支付中";
            case 2 -> "已支付";
            case 3 -> "已取消";
            case 4 -> "已退款";
            default -> "未知";
        };
    }

    /**
     * 构建支付准备返回值
     */
    private PaymentPrepareVO buildPaymentPrepareResult(OrderInfo order, PayResponse payResponse) {
        PaymentPrepareVO vo = new PaymentPrepareVO();
        vo.setOrderId(order.getOrderId());
        vo.setOrderNo(order.getOrderNo());
        vo.setAmount(order.getActualAmount());
        vo.setDescription(order.getSnapshotConcertName());
        vo.setExpireTime(order.getExpireTime());
        vo.setPrepayToken(order.getOrderNo() + "_" + System.currentTimeMillis());
        vo.setStatus(order.getStatus());
        // 支付网关参数
        if (payResponse != null) {
            vo.setPayUrl(payResponse.getPayUrl());
            vo.setPrepayId(payResponse.getPrepayId());
            vo.setTransactionId(payResponse.getTransactionId());
            vo.setExtraParams(payResponse.getExtraParams());
        }
        return vo;
    }
}