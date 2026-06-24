package com.example.ticket_system.main_business.order.dto;

import lombok.Data;

/**
 * 订单查询请求DTO
 */
@Data
public class OrderQueryDTO {

    /** 页码（从1开始，默认1） */
    private Integer pageNum = 1;

    /** 每页数量（默认10，最大50） */
    private Integer pageSize = 10;

    /** 订单状态筛选（可选）：0待支付 1已支付 2已取消 3已退款 4已完成 */
    private Integer status;

    /** 演唱会名称模糊搜索（可选） */
    private String concertName;

    /** 订单号搜索（可选） */
    private String orderNo;
}