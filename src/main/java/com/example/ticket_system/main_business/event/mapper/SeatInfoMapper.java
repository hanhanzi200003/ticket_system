package com.example.ticket_system.main_business.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket_system.main_business.event.entity.SeatInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 座位信息 Mapper
 */
@Mapper
public interface SeatInfoMapper extends BaseMapper<SeatInfo> {
}