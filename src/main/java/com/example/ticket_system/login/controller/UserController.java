package com.example.ticket_system.login.controller;

import com.example.ticket_system.config.utils.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/user")
public class UserController {

    /**
     * 检查用户状态
     * 用于前端页面加载时验证 token 有效性和用户状态
     */
    @GetMapping("/check")
    public Result<Void> checkUser() {
        return Result.success("用户状态正常", null);
    }
}
