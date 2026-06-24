package com.example.ticket_system.main_business.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 取消订单请求DTO
 */
@Data
public class CancelOrderDTO {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    /** 取消原因（可选） */
    private String cancelReason;
}