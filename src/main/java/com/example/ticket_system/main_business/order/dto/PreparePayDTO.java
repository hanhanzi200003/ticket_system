package com.example.ticket_system.main_business.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 支付准备请求 DTO
 */
@Data
public class PreparePayDTO {

    /** 订单ID */
    @NotNull(message = "订单ID不能为空")
    private Long orderId;
}