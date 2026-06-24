package com.example.ticket_system.main_business.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 票档信息实体类
 */
@Data
@TableName("ticket_tier")
public class TicketTier {
    
    @TableId(value = "tier_id", type = IdType.ASSIGN_ID)
    private Long tierId;
    
    @TableField("concert_id")
    private Long concertId;
    
    @TableField("area_name")
    private String areaName;
    
    @TableField("price")
    private BigDecimal price;
    
    @TableField("total_stock")
    private Integer totalStock;
    
    @TableField("available_stock")
    private Integer availableStock;
    
    @TableField("create_time")
    private LocalDateTime createTime;
    
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 排序
     */
    @TableField("sort_order")
    private Integer sortOrder;
}
