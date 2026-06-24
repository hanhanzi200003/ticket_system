package com.example.ticket_system.main_business.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 支付订单请求DTO
 */
@Data
public class PayOrderDTO {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;
}