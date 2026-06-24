package com.example.ticket_system.main_business.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket_system.main_business.event.entity.TicketTier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TicketTierMapper extends BaseMapper<TicketTier> {

    /**
     * 扣减库存（乐观锁，保证扣减后库存不为负）
     *
     * @param tierId   票档ID
     * @param quantity 扣减数量
     * @return 影响行数（0表示扣减失败）
     */
    @Update("UPDATE ticket_tier SET available_stock = available_stock - #{quantity} " +
            "WHERE tier_id = #{tierId} AND available_stock >= #{quantity}")
    int updateStock(Long tierId, Integer quantity);

    /**
     * 归还库存（取消订单时使用）
     *
     * @param tierId   票档ID
     * @param quantity 归还数量
     * @return 影响行数
     */
    @Update("UPDATE ticket_tier SET available_stock = available_stock + #{quantity} " +
            "WHERE tier_id = #{tierId}")
    int restoreStock(Long tierId, Integer quantity);
}
