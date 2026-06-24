package com.example.ticket_system.main_business.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 演唱会批量退款任务
 * <p>
 * 演唱会取消时，分批处理所有已支付订单的退款。
 * last_order_id 作为断点，支持断点续传。
 * 由 MQ 消费者立即处理，定时扫描器（60s）兜底。
 */
@Data
@TableName("concert_refund_job")
public class ConcertRefundJob {

    // ==================== 状态常量 ====================

    /** 待开始 */
    public static final int STATUS_PENDING = 0;

    /** 执行中 */
    public static final int STATUS_PROCESSING = 1;

    /** 完成 */
    public static final int STATUS_COMPLETED = 2;

    /**
     * 主键
     */
    @TableId(value = "job_id", type = IdType.ASSIGN_ID)
    private Long jobId;

    /**
     * 演唱会ID
     */
    @TableField("concert_id")
    private Long concertId;

    /**
     * 任务状态
     * 0 待开始
     * 1 执行中
     * 2 完成
     */
    @TableField("status")
    private Integer status;

    /**
     * 总订单数
     */
    @TableField("total_count")
    private Integer totalCount;

    /**
     * 已处理数
     */
    @TableField("processed_count")
    private Integer processedCount;

    /**
     * 断点：最后处理的订单ID（用于分批续传）
     */
    @TableField("last_order_id")
    private Long lastOrderId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}