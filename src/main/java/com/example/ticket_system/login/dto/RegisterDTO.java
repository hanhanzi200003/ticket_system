package com.example.ticket_system.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDTO {
    // 手机号或邮箱至少提供一个
    private String phone;
    private String email;
    
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20位之间")
    private String password;
    
    @NotBlank(message = "确认密码不能为空")
    private String rePassword;
    
    // 昵称（可选，默认使用手机号/邮箱前缀）
    private String nickname;
    
    // 角色（默认为user）
    private String role;

    //商家注册才需要注册码
    private String verificationCode;
}
