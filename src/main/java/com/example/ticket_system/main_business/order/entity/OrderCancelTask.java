package com.example.ticket_system.main_business.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单取消任务实体类
 * <p>
 * 用于异步化取消订单流程，支持：
 * - 超时取消（10分钟未支付）
 * - 用户主动取消
 * - 演唱会取消（商家/管理员操作）
 * <p>
 * 任务通过定时扫描重试，解耦取消流程中的 Redis 释放 + DB 更新操作。
 */
@Data
@TableName("order_cancel_task")
public class OrderCancelTask {

    // ==================== 任务类型常量（task_type） ====================

    /** 超时取消：下单后 10 分钟未支付 */
    public static final int TASK_TYPE_TIMEOUT = 1;

    /** 用户主动取消 */
    public static final int TASK_TYPE_USER_CANCEL = 2;

    /** 演唱会取消（商家/管理员操作） */
    public static final int TASK_TYPE_CONCERT_CANCEL = 3;

    // ==================== 任务状态常量（status） ====================

    /** 待执行 */
    public static final int STATUS_PENDING = 0;

    /** 执行中 */
    public static final int STATUS_PROCESSING = 1;

    /** 成功 */
    public static final int STATUS_SUCCESS = 2;

    /** 失败 */
    public static final int STATUS_FAILED = 3;

    /** 最大重试次数 */
    public static final int MAX_RETRY = 5;


    // ==================== 字段 ====================

    /** 任务ID（雪花算法） */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 任务编号（唯一，对外展示） */
    @TableField("task_no")
    private String taskNo;

    /** 订单ID */
    @TableField("order_id")
    private Long orderId;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 任务类型：1超时取消 2用户取消 3演唱会取消 */
    @TableField("task_type")
    private Integer taskType;

    /** 任务状态：0待执行 1执行中 2成功 3失败 */
    @TableField("status")
    private Integer status;

    /** 已重试次数 */
    @TableField("retry_count")
    private Integer retryCount;

    /** 最大重试次数（默认 5） */
    @TableField("max_retry")
    private Integer maxRetry;

    /** 下次重试时间 */
    @TableField("next_retry_time")
    private LocalDateTime nextRetryTime;

    /** 扩展业务数据（JSON） */
    @TableField("payload")
    private String payload;

    /** 执行时间 */
    @TableField("execute_time")
    private LocalDateTime executeTime;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;
}