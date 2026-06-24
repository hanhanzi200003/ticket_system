package com.example.ticket_system.main_business.event.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待审核演唱会列表项VO
 */
@Data
public class PendingConcertVO {
    
    private Long concertId;
    private Long merchantId;
    private Long artistId;
    private String concertName;
    private String artistName; // 明星姓名（关联查询）
    private String city;
    private String venueName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private Integer auditStatus;        // 保留兼容，0待审核 1通过 2拒绝
    private Integer lifecycleStatus;    // 0草稿 1待上架 2售票中 3已结束 4已下架
    private Integer operationStatus;    // 0无操作 1取消申请中 2退款处理中 3已完成取消流程
    private LocalDateTime createTime;
}
