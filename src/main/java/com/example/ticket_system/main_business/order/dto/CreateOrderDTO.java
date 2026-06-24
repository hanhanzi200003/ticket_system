package com.example.ticket_system.main_business.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建订单请求DTO
 */
@Data
public class CreateOrderDTO {

    @NotNull(message = "演唱会ID不能为空")
    private Long concertId;

    @NotNull(message = "票档ID不能为空")
    private Long tierId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity;

    /** 优惠券ID（可选） */
    private Long couponId;
}