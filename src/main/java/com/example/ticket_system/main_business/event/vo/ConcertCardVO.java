package com.example.ticket_system.main_business.event.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 演唱会卡片VO（用于列表展示）
 */
@Data
public class ConcertCardVO {
    
    /**
     * 演唱会ID
     */
    private Long concertId;
    
    /**
     * 海报URL（取第一张）
     */
    private String posterUrl;
    
    /**
     * 演唱会名称
     */
    private String concertName;
    
    /**
     * 艺人名称
     */
    private String artistName;
    
    /**
     * 城市
     */
    private String city;
    
    /**
     * 演出开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 最低票价
     */
    private BigDecimal minPrice;
    
    /**
     * 业务生命周期：0草稿 1待上架 2售票中 3已结束 4已下架
     * 保持字段名 status 以兼容前端
     */
    private Integer status;
    
    /**
     * 状态描述
     */
    private String statusDesc;

    /** 业务生命周期（显式） */
    private Integer lifecycleStatus;
    
    /** 操作流程 */
    private Integer operationStatus;
}
