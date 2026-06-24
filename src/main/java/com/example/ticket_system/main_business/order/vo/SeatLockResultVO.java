package com.example.ticket_system.main_business.order.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 座位锁定结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatLockResultVO {

    /** 是否锁定成功 */
    private boolean success;

    /** 锁定的座位标签列表 */
    private List<String> seatLabels;

    /** 错误信息 */
    private String errorMsg;
}