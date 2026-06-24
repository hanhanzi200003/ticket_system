package com.example.ticket_system.transactional_outbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表（Transactional Outbox）
 *
 * 用于 RabbitMQ 不可用时的消息持久化与补偿发送。
 * 业务操作 → 写入本地消息表 → 尝试发送 MQ → 成功则标记已发送，
 * 失败则由定时任务扫描重发，保证消息不丢失。
 *
 * 对应建表语句：
 * <pre>
 * CREATE TABLE mq_message (
 *      id BIGINT PRIMARY KEY AUTO_INCREMENT,
 *      message_id VARCHAR(64) NOT NULL COMMENT '业务幂等ID',
 *      event_type VARCHAR(50) NOT NULL COMMENT '事件类型：ORDER_PERSIST / ORDER_CANCEL_PERSIST',
 *      business_id BIGINT NOT NULL COMMENT '业务ID（如订单ID）',
 *      content TEXT NOT NULL COMMENT '消息体JSON',
 *      status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待发送 1=已发送 2=发送失败(超过重试次数)',
 *      retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
 *      error_msg VARCHAR(500) DEFAULT NULL COMMENT '最近一次错误信息',
 *      create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *      update_time DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
 *      INDEX idx_status_retry (status, retry_count, create_time)
 * );
 * </pre>
 */
@Data
@TableName("mq_message")
public class MqMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务幂等ID */
    @TableField("message_id")
    private String messageId;

    /** 事件类型：ORDER_PERSIST / ORDER_CANCEL_PERSIST */
    @TableField("event_type")
    private String eventType;

    /** 业务ID（如订单ID） */
    @TableField("business_id")
    private Long businessId;

    /** 消息体JSON */
    @TableField("content")
    private String content;

    /** 0=待发送 1=已发送 2=发送失败(超过重试次数) */
    @TableField("status")
    private Integer status;

    /** 已重试次数 */
    @TableField("retry_count")
    private Integer retryCount;

    /** 最近一次错误信息 */
    @TableField("error_msg")
    private String errorMsg;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}