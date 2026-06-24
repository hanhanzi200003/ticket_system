package com.example.ticket_system.main_business.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 删除订单请求DTO（逻辑删除）
 */
@Data
public class DeleteOrderDTO {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;
}