package com.example.ticket_system.admin.service;

public interface AdminService {
    /**
     * 踢人下线（根据用户ID）
     */
    void kickUser(Long userId);

    /**
     * 封禁/解封用户
     */
    void toggleUserStatus(Long userId, Integer status);
}
