package com.example.ticket_system.login.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class UserVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String phone;
    private String email;
    private String nickname;
    private String avatarUrl;
    private String role;
    private Integer status;
    private Integer realNameVerified;
    private String token; // 登录成功后返回token
}
