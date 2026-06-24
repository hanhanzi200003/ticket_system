package com.example.ticket_system.admin.controller;

import com.example.ticket_system.config.utils.Result;
import com.example.ticket_system.admin.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    /**
     * 踢人下线
     */
    @PostMapping("/kick/{userId}")
    public Result<Void> kickUser(@PathVariable Long userId){
        adminService.kickUser(userId);
        return Result.success("已踢下线！", null);
    }

    /**
     * 封禁/解封用户
     * status: 0-封禁 1-正常
     */
    @PostMapping("/toggleStatus/{userId}")
    public Result<Void> toggleUserStatus(@PathVariable Long userId, @RequestParam Integer status){
        adminService.toggleUserStatus(userId, status);
        return Result.success("操作成功！", null);
    }
}
