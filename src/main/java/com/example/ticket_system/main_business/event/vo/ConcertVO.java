package com.example.ticket_system.main_business.event.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 演唱会发布结果VO
 */
@Data
public class ConcertVO {
    
    private Long concertId;
    private Long artistId; // 明星ID
    private String name;
    private String artist;
    private String posterUrl;
    private String city;
    private String venueName;
    private String address;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    // 售票信息
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private Integer maxPurchaseQuantity;
    
    // 购票须知
    private String purchaseNotice;
    
    // 演唱会详细介绍
    private String description;
    
    // 海报列表（JSON数组）
    private String posters;
    
    // 审核状态：0待审核 1已通过 2已拒绝 3已封禁
    private Integer auditStatus;
    private String auditRemark;
    
    // 业务生命周期：0草稿 1待上架 2售票中 3已结束 4已下架
    private Integer status;
    private Integer lifecycleStatus;
    private Integer operationStatus;
    
    private LocalDateTime createTime;
}
