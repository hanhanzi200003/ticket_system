package com.example.ticket_system.admin.service;

import com.example.ticket_system.login.entity.User;

import java.util.List;

public interface AdminService {
    /**
     * 踢人下线（根据用户ID）
     */
    void kickUser(Long userId);

    /**
     * 封禁/解封用户
     */
    void toggleUserStatus(Long userId, Integer status);

    /**
     * 查询用户列表（按角色筛选）
     */
    List<User> getUserList(String role);

    /**
     * 获取用户统计信息
     */
    long getUserCount(String role);
}
