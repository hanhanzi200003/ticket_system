package com.example.ticket_system.main_business.order.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket_system.main_business.order.entity.OrderCancelTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单取消任务 Mapper
 */
@Mapper
public interface OrderCancelTaskMapper extends BaseMapper<OrderCancelTask> {

    /**
     * 根据任务编号查询
     */
    default OrderCancelTask selectByTaskNo(String taskNo) {
        return selectOne(new LambdaQueryWrapper<OrderCancelTask>()
                .eq(OrderCancelTask::getTaskNo, taskNo));
    }
}