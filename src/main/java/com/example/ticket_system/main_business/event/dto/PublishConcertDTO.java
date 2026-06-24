package com.example.ticket_system.main_business.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布演唱会请求DTO
 */
@Data
public class PublishConcertDTO {
    
    @NotBlank(message = "演唱会名称不能为空")
    @Size(max = 200, message = "演唱会名称不能超过200个字符")
    private String name;
    
    @NotNull(message = "明星ID不能为空")
    private Long artistId; // 明星ID
    
    // 封面图URL（单张）
    @NotBlank(message = "封面图不能为空")
    private String coverImage;
    
    // 详情图URL列表（最多4张）
    private List<String> detailImages;
    
    @NotBlank(message = "城市不能为空")
    @Size(max = 50, message = "城市名称不能超过50个字符")
    private String city;
    
    @NotBlank(message = "场馆名称不能为空")
    @Size(max = 200, message = "场馆名称不能超过200个字符")
    private String venueName;
    
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 500, message = "地址不能超过500个字符")
    private String address;
    
    @NotNull(message = "演出开始时间不能为空")
    private LocalDateTime startTime;
    
    @NotNull(message = "演出结束时间不能为空")
    private LocalDateTime endTime;
    
    // 售票信息
    @NotNull(message = "开售时间不能为空")
    private LocalDateTime saleStartTime;
    
    @NotNull(message = "停售时间不能为空")
    private LocalDateTime saleEndTime;
    
    @Min(value = 1, message = "限购数量至少为1")
    @Max(value = 10, message = "限购数量不能超过10")
    private Integer maxPurchaseQuantity = 4;
    
    // 购票须知
    private String purchaseNotice;
    
    // 演唱会详细介绍
    private String description;
    
    // 票档信息列表
    @NotEmpty(message = "至少需要一个票档")
    @Valid
    private List<TicketTierDTO> ticketTiers;
    
    /**
     * 票档DTO（内部类）
     */
    @Data
    public static class TicketTierDTO {
        
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
