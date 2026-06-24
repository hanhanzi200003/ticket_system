package com.example.ticket_system.main_business.order.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket_system.main_business.order.entity.RefundTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 退款任务 Mapper
 */
@Mapper
public interface RefundTaskMapper extends BaseMapper<RefundTask> {

    /**
     * 根据退款编号查询
     */
    default RefundTask selectByRefundNo(String refundNo) {
        return selectOne(new LambdaQueryWrapper<RefundTask>()
                .eq(RefundTask::getRefundNo, refundNo));
    }

    /**
     * 根据订单ID查询退款任务
     */
    default RefundTask selectByOrderId(Long orderId) {
        return selectOne(new LambdaQueryWrapper<RefundTask>()
                .eq(RefundTask::getOrderId, orderId)
                .orderByDesc(RefundTask::getCreateTime)
                .last("LIMIT 1"));
    }

    /**
     * 根据订单ID和退款类型查询退款任务（唯一性检查）
     * <p>
     * 用于防止同一订单的同类型退款重复创建
     */
    default RefundTask selectByOrderIdAndRefundType(Long orderId, Integer refundType) {
        return selectOne(new LambdaQueryWrapper<RefundTask>()
                .eq(RefundTask::getOrderId, orderId)
                .eq(RefundTask::getRefundType, refundType)
                .orderByDesc(RefundTask::getCreateTime)
                .last("LIMIT 1"));
    }
}