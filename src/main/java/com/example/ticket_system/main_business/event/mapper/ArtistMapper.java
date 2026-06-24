package com.example.ticket_system.main_business.event.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket_system.main_business.event.entity.Artist;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArtistMapper extends BaseMapper<Artist> {
}
