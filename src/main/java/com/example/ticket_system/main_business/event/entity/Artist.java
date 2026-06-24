package com.example.ticket_system.main_business.event.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 明星实体类
 */
@Data
@TableName("artist")
public class Artist {
    
    @TableId(value = "artist_id", type = IdType.ASSIGN_ID)
    private Long artistId;
    
    @TableField("name")
    private String name;
    
    @TableField("avatar_url")
    private String avatarUrl;
    
    @TableField("introduction")
    private String introduction;
    
    @TableField("create_time")
    private LocalDateTime createTime;
    
    @TableField("update_time")
    private LocalDateTime updateTime;
}
