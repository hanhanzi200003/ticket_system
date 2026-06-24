package com.example.ticket_system.main_business.order.vo;

import lombok.Data;

import java.util.List;

/**
 * 订单分页VO
 */
@Data
public class OrderPageVO {

    /** 当前页码 */
    private Integer pageNum;

    /** 每页数量 */
    private Integer pageSize;

    /** 总记录数 */
    private Long total;

    /** 总页数 */
    private Integer pages;

    /** 订单列表 */
    private List<OrderVO> list;
}