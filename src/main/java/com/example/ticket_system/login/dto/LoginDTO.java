package com.example.ticket_system.login.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    // 手机号或邮箱（前端传入，后端自动识别）
    @NotBlank(message = "登录账号不能为空")
    private String account; // 可以是手机号或邮箱
    
    @NotBlank(message = "密码不能为空")
    private String password;
}
