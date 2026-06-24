package com.example.ticket_system.main_business.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket_system.main_business.order.entity.ConcertRefundJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 演唱会批量退款任务 Mapper
 */
@Mapper
public interface ConcertRefundJobMapper extends BaseMapper<ConcertRefundJob> {
}