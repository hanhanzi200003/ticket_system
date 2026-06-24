package com.example.ticket_system.main_business.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 更新演唱会请求DTO
 */
@Data
public class UpdateConcertDTO {
    
    @NotNull(message = "演唱会ID不能为空")
    private Long concertId;
    
    // === 基础信息（可选）===
    private String name;
    private Long artistId;
    private String coverImage; // 封面图
    private List<String> detailImages; // 详情图列表（最多4张）
    private String city;
    private String venueName;
    private String address;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    // === 售票信息（可选）===
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private Integer maxPurchaseQuantity;
    
    // === 购票须知和介绍（可选）===
    private String purchaseNotice;
    private String description;
    
    // === 票档信息（可选，传了表示要更新票档）===
    @Valid
    private List<TicketTierDTO> ticketTiers;
    
    /**
     * 票档DTO
     */
    @Data
    public static class TicketTierDTO {
        
        private Long tierId; // 有ID表示修改，无ID表示新增
        
        @NotBlank(message = "区域名称不能为空")
        @Size(max = 100, message = "区域名称不能超过100个字符")
        private String areaName;
        
        @NotNull(message = "票价不能为空")
        @DecimalMin(value = "0.01", message = "票价必须大于0")
        private BigDecimal price;
        
        @NotNull(message = "库存不能为空")
        @Min(value = 1, message = "库存至少为1")
        private Integer totalStock;
    }
}
