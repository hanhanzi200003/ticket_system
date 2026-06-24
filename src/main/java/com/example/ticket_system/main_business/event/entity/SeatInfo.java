package com.example.ticket_system.main_business.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 座位信息实体类
 */
@Data
@TableName("seat_info")
public class SeatInfo {

    @TableId(value = "seat_id", type = IdType.ASSIGN_ID)
    private Long seatId;

    /**
     * 演唱会ID
     */
    @TableField("concert_id")
    private Long concertId;

    /**
     * 票档ID
     */
    @TableField("tier_id")
    private Long tierId;

    /**
     * 座位编号
     * VIP-001
     * VIP-002
     */
    @TableField("seat_no")
    private String seatNo;

    /**
     * 状态
     * 0 可售
     * 1 已售（含锁定，MySQL 不区分锁定/已售，仅作最终数据源用于 Redis 恢复）
     */
    @TableField("status")
    private Integer status;

    /**
     * 对应订单号
     * 锁定或售出时记录
     */
    @TableField("order_no")
    private String orderNo;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}