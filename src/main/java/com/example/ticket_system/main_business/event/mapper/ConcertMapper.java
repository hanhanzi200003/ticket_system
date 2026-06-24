package com.example.ticket_system.main_business.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket_system.main_business.event.entity.Concert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConcertMapper extends BaseMapper<Concert> {
}
