package com.example.ticket_system.main_business.event.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新演唱会图片请求DTO
 */
@Data
public class UpdateConcertImagesDTO {
    
    /**
     * 演唱会ID
     */
    @NotBlank(message = "演唱会ID不能为空")
    private Long concertId;
    
    /**
     * 封面图URL（单张）
     */
    private String coverImage;
    
    /**
     * 详情图URL列表（最多4张）
     */
    private java.util.List<String> detailImages;
}
