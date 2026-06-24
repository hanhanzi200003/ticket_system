package com.example.ticket_system.main_business.event.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审核演唱会请求DTO
 */
@Data
public class AuditConcertDTO {
    
    @NotNull(message = "演唱会ID不能为空")
    private Long concertId;
    
    @NotNull(message = "审核结果不能为空")
    // 1: 通过, 2: 拒绝
    private Integer auditResult;
    
    // 审核备注（拒绝时必填）
    private String auditRemark;
}
