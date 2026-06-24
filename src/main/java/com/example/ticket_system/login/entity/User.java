package com.example.ticket_system.login.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(value = "user_id", type = IdType.ASSIGN_ID)
    private Long userId;
    
    @TableField("phone")
    private String phone;
    
    @TableField("email")
    private String email;
    
    @TableField("password")
    private String password;
    
    @TableField("role")
    private String role;
    
    @TableField("status")
    private Integer status;
    
    @TableField("nickname")
    private String nickname;
    
    @TableField("avatar_url")
    private String avatarUrl;
    
    @TableField("real_name_verified")
    private Integer realNameVerified;
    
    @TableField("create_time")
    private LocalDateTime createTime;
}
