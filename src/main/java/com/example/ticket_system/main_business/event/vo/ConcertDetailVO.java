package com.example.ticket_system.main_business.event.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户查看演唱会详情VO
 */
@Data
public class ConcertDetailVO {
    
    private Long concertId;
    private Long artistId;
    private String concertName;
    private String artistName;
    private String coverImage; // 封面图URL
    private List<String> detailImages; // 详情图列表（最多4张）
    private String city;
    private String venueName;
    private String address;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    // 售票信息
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private Integer maxPurchaseQuantity;
    
    // 购票须知和介绍
    private String purchaseNotice;
    private String description;
    
    // 业务生命周期
    private Integer status;                 // 保持字段名兼容前端 → 由 lifecycleStatus 填充
    private Integer lifecycleStatus;        // 0草稿 1待上架 2售票中 3已结束 4已下架
    private Integer operationStatus;        // 0无操作 1取消申请中 2退款处理中 3已完成取消流程
    
    // 票档列表
    private List<TicketTierVO> ticketTiers;
    
    /**
     * 票档VO
     */
    @Data
    public static class TicketTierVO {
        private Long tierId;
        private String areaName;
        private BigDecimal price;
        private Integer totalStock;
        private Integer availableStock;
    }
}
