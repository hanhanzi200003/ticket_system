package com.example.ticket_system.main_business.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.ticket_system.config.utils.RabbitMQUtil;
import com.example.ticket_system.config.utils.RedisUtil;
import com.example.ticket_system.config.utils.SnowflakeIdGenerator;
import com.example.ticket_system.main_business.order.entity.OrderCancelTask;
import com.example.ticket_system.main_business.order.entity.OrderInfo;
import com.example.ticket_system.main_business.order.entity.RefundTask;
import com.example.ticket_system.main_business.event.entity.SeatInfo;
import com.example.ticket_system.main_business.event.mapper.SeatInfoMapper;
import com.example.ticket_system.main_business.order.mapper.OrderCancelTaskMapper;
import com.example.ticket_system.main_business.order.mapper.OrderMapper;
import com.example.ticket_system.main_business.order.service.OrderCancelTaskService;
import com.example.ticket_system.main_business.order.service.RefundTaskService;
import com.example.ticket_system.main_business.order.service.SeatInfoService;
import com.example.ticket_system.main_business.order.service.SeatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单取消任务服务实现
 * <p>
 * 核心原则：
 * - createCancelTask：只 INSERT 任务 + 发 MQ，不做业务
 * - executeTask：幂等执行，MQ 消费者和定时任务共用此方法
 */
@Slf4j
@Service
public class OrderCancelTaskServiceImpl implements OrderCancelTaskService {

    /** 取消事件 MQ routing key */
    private static final String CANCEL_EVENT_ROUTING_KEY = "order.cancel.event";

    /** 分布式锁前缀 */
    private static final String TASK_LOCK_PREFIX = "task:lock:";

    /** 分布式锁过期时间（秒） */
    private static final long TASK_LOCK_EXPIRE_SECONDS = 300; // 5分钟

    @Autowired
    private OrderCancelTaskMapper taskMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private SeatInfoService seatInfoService;

    @Autowired
    private SeatInfoMapper seatInfoMapper;

    @Autowired
    private SeatService seatService;

    @Autowired
    private SnowflakeIdGenerator idGenerator;

    @Autowired
    private RabbitMQUtil rabbitMQUtil;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefundTaskService refundTaskService;

    // ==================== 创建任务 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createCancelTask(Long orderId, Long userId, Integer taskType) {
        // 1. 生成 ID 和任务编号
        Long taskId = idGenerator.nextId();
        String taskNo = generateTaskNo();

        // 2. 构建任务实体
        OrderCancelTask task = new OrderCancelTask();
        task.setId(taskId);
        task.setTaskNo(taskNo);
        task.setOrderId(orderId);
        task.setUserId(userId);
        task.setTaskType(taskType);
        task.setStatus(OrderCancelTask.STATUS_PENDING);
        task.setRetryCount(0);
        task.setMaxRetry(5);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());

        // 3. INSERT
        taskMapper.insert(task);

        log.info("取消任务已创建：taskNo={}, orderId={}, userId={}, taskType={}",
                taskNo, orderId, userId, taskType);

        // 4. 发送 MQ 通知消费
        rabbitMQUtil.convertAndSend("order.exchange", CANCEL_EVENT_ROUTING_KEY, taskNo);
        log.info("取消事件 MQ 已发送：taskNo={}", taskNo);

        return taskNo;
    }

    // ==================== 执行任务（核心 — 幂等） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeTask(String taskNo) {
        // 0. Redis 分布式锁：保证多节点/多线程环境下任务只被一个执行者处理
        String lockKey = TASK_LOCK_PREFIX + taskNo;
        if (!redisUtil.setNx(lockKey, taskNo, TASK_LOCK_EXPIRE_SECONDS)) {
            log.info("任务正在被其他节点执行，跳过：taskNo={}", taskNo);
            return;
        }
        try {
            // 1. 查询任务
            OrderCancelTask task = taskMapper.selectByTaskNo(taskNo);
            if (task == null) {
                log.warn("执行取消任务：任务不存在 taskNo={}", taskNo);
                return;
            }

            // 2. 乐观锁：只有 PENDING 状态才能执行
            int updated = taskMapper.update(null, new LambdaUpdateWrapper<OrderCancelTask>()
                    .eq(OrderCancelTask::getTaskNo, taskNo)
                    .eq(OrderCancelTask::getStatus, OrderCancelTask.STATUS_PENDING)
                    .set(OrderCancelTask::getStatus, OrderCancelTask.STATUS_PROCESSING)
                    .set(OrderCancelTask::getRetryCount, task.getRetryCount() + 1)
                    .set(OrderCancelTask::getExecuteTime, LocalDateTime.now()));
            if (updated == 0) {
                log.info("取消任务已被其他节点执行，跳过：taskNo={}", taskNo);
                return;
            }

            // 重新查询任务（获取最新 retryCount）
            task = taskMapper.selectByTaskNo(taskNo);

            // 3. 查询订单
            OrderInfo order = orderMapper.selectById(task.getOrderId());
            if (order == null) {
                log.error("取消任务：订单不存在 orderId={}", task.getOrderId());
                markTask(task, OrderCancelTask.STATUS_FAILED, "订单不存在");
                return;
            }

            // 4. 判断是否需要取消
            Integer newStatus = determineNewStatus(task.getTaskType(), order.getStatus());
            if (newStatus == null) {
                log.info("取消任务：订单状态无需变更 orderId={}, currentStatus={}",
                        order.getOrderId(), order.getStatus());
                markTask(task, OrderCancelTask.STATUS_SUCCESS, "无需取消");
                return;
            }

            // 5. 解析座位标签
            final List<String> seatLabels = parseSeatLabels(order.getSeatsJson());
            final Long finalConcertId = order.getConcertId();
            final Long finalTierId = order.getTierId();

            // 6. 在同一个事务中：更新 MySQL 座位状态 + 更新订单状态
            //    Redis 释放放到事务提交后（registerSynchronization），保证 MySQL 原子性
            LocalDateTime now = LocalDateTime.now();

            if (!seatLabels.isEmpty()) {
                // MySQL 座位状态 1→0（已锁定/已售 → 可售）
                seatInfoMapper.update(null, new LambdaUpdateWrapper<SeatInfo>()
                        .eq(SeatInfo::getConcertId, finalConcertId)
                        .eq(SeatInfo::getTierId, finalTierId)
                        .in(SeatInfo::getSeatNo, seatLabels)
                        .eq(SeatInfo::getStatus, 1)
                        .set(SeatInfo::getStatus, 0));
                log.info("取消任务：MySQL 座位已更新为可售 taskNo={}, seatCount={}", taskNo, seatLabels.size());
            }

            if (newStatus == 4) {
                // 需要退款：创建退款任务（内部会更新订单为已取消+设置cancelTime）
                refundTaskService.createRefundTask(order, getRefundType(task.getTaskType()));
                markTask(task, OrderCancelTask.STATUS_SUCCESS, "退款任务已创建");
            } else {
                // 普通取消（无需退款）：直接更新订单状态
                order.setStatus(newStatus);
                order.setUpdateTime(now);
                if (newStatus == 3) {
                    order.setCancelTime(now);
                }
                orderMapper.updateById(order);
                markTask(task, OrderCancelTask.STATUS_SUCCESS, "取消成功");
            }

            // 7. 事务提交后释放 Redis 座位（Redis 不在 Spring 事务中，必须在 MySQL 提交后执行）
            if (!seatLabels.isEmpty() && TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            seatService.releaseSeats(finalConcertId, finalTierId, seatLabels);
                            log.info("取消任务：Redis 座位已释放 taskNo={}, seatCount={}", taskNo, seatLabels.size());
                        } catch (Exception e) {
                            log.error("取消任务：Redis 座位释放失败（后续由 restoreSeatsFromDb 兜底）taskNo={}", taskNo, e);
                        }
                    }
                });
            } else if (!seatLabels.isEmpty()) {
                // 不在事务中（理论上不会走到这里，防御性代码）
                seatService.releaseSeats(finalConcertId, finalTierId, seatLabels);
            }

            log.info("取消任务执行成功：taskNo={}, orderNo={}, newStatus={}",
                    taskNo, order.getOrderNo(), newStatus);

            // 8. 标记成功
            markTask(task, OrderCancelTask.STATUS_SUCCESS, "取消成功");

        } catch (Exception e) {
            log.error("取消任务执行失败：taskNo={}", taskNo, e);
            // catch 中无法获取 task，直接按 taskNo 更新状态
            taskMapper.update(null, new LambdaUpdateWrapper<OrderCancelTask>()
                    .eq(OrderCancelTask::getTaskNo, taskNo)
                    .eq(OrderCancelTask::getStatus, OrderCancelTask.STATUS_PROCESSING)
                    .set(OrderCancelTask::getStatus, OrderCancelTask.STATUS_FAILED)
                    .set(OrderCancelTask::getUpdateTime, LocalDateTime.now()));
        } finally {
            // 释放分布式锁
            redisUtil.delete(lockKey, taskNo);
        }
    }

    // ==================== 扫描待执行任务 ====================

    @Override
    public List<OrderCancelTask> scanPendingTasks(int limit) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<OrderCancelTask>()
                        .in(OrderCancelTask::getStatus,
                                OrderCancelTask.STATUS_PENDING,
                                OrderCancelTask.STATUS_FAILED)
                        .lt(OrderCancelTask::getRetryCount, OrderCancelTask.MAX_RETRY)
                        // 优先处理待执行的，然后是失败时间最久的
                        .orderByAsc(OrderCancelTask::getStatus)
                        .orderByAsc(OrderCancelTask::getUpdateTime)
                        .last("LIMIT " + limit));
    }

    // ==================== 私有方法 ====================

    /**
     * 根据任务类型和订单当前状态，确定目标状态
     *
     * @param taskType     任务类型
     * @param orderStatus  订单当前状态
     * @return 目标状态，null 表示无需变更
     */
    private Integer determineNewStatus(Integer taskType, Integer orderStatus) {
        // 已取消(3)/已退款(4) 均无需再处理
        if (orderStatus == 3 || orderStatus == 4) {
            return null;
        }

        switch (taskType) {
            case OrderCancelTask.TASK_TYPE_TIMEOUT:
                // 超时取消：仅处理待支付(0)或支付中(1)
                return (orderStatus == 0 || orderStatus == 1) ? 3 : null;
            case OrderCancelTask.TASK_TYPE_USER_CANCEL:
                // 用户取消：待支付/支付中→已取消(3)，已支付→已退款(4)
                if (orderStatus == 0 || orderStatus == 1) return 3;
                if (orderStatus == 2) return 4;
                return null;
            case OrderCancelTask.TASK_TYPE_CONCERT_CANCEL:
                // 演唱会取消：全部→已取消(3)/已退款(4)
                if (orderStatus == 0 || orderStatus == 1) return 3;
                if (orderStatus == 2) return 4;
                return null;
            default:
                return null;
        }
    }

    /**
     * 更新任务状态
     */
    private void markTask(OrderCancelTask task, Integer status, String remark) {
        taskMapper.update(null, new LambdaUpdateWrapper<OrderCancelTask>()
                .eq(OrderCancelTask::getId, task.getId())
                .set(OrderCancelTask::getStatus, status)
                .set(OrderCancelTask::getUpdateTime, LocalDateTime.now()));
        log.info("取消任务状态更新：taskNo={}, {} → {}, remark={}",
                task.getTaskNo(), getStatusText(task.getStatus()),
                getStatusText(status), remark);
    }

    /**
     * 生成任务编号：CT + yyyyMMddHHmmss + 4位随机数
     */
    private String generateTaskNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = new Random().nextInt(9000) + 1000;
        return "CT" + timestamp + random;
    }

    /**
     * 从 seatsJson 解析座位标签
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

    private String getStatusText(Integer status) {
        if (status == null) return "null";
        switch (status) {
            case 0: return "待执行";
            case 1: return "执行中";
            case 2: return "成功";
            case 3: return "失败";
            default: return "未知(" + status + ")";
        }
    }

    /**
     * 根据取消任务类型获取退款原因类型
     */
    private Integer getRefundType(Integer taskType) {
        switch (taskType) {
            case OrderCancelTask.TASK_TYPE_USER_CANCEL:
                return RefundTask.REFUND_TYPE_USER;
            case OrderCancelTask.TASK_TYPE_CONCERT_CANCEL:
                return RefundTask.REFUND_TYPE_CONCERT_CANCEL;
            default:
                return RefundTask.REFUND_TYPE_ADMIN;
        }
    }
}