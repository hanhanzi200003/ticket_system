package com.example.ticket_system.admin.service.impl;

import com.example.ticket_system.admin.service.AdminService;
import com.example.ticket_system.config.exception.AllException;
import com.example.ticket_system.config.utils.TokenUtil;
import com.example.ticket_system.login.entity.User;
import com.example.ticket_system.login.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminServiceImpl implements AdminService {

    @Autowired
    private TokenUtil tokenUtil;

    @Autowired
    private UserMapper userMapper;

    @Override
    public void kickUser(Long userId){
        User user = userMapper.selectById(userId);
        if(user == null){
            throw new AllException(404, "用户不存在！");
        }
        // 删除该用户的token
        tokenUtil.deleteTokenByUserId(userId);
    }

    @Override
    public void toggleUserStatus(Long userId, Integer status){
        User user = userMapper.selectById(userId);
        if(user == null){
            throw new AllException(404, "用户不存在！");
        }
        if(status != 0 && status != 1){
            throw new AllException(400, "状态参数错误！");
        }
        user.setStatus(status);
        userMapper.updateById(user);

        // 如果是封禁（status=0），同时踢下线
        if(status == 0){
            tokenUtil.deleteTokenByUserId(userId);
        }
    }
}
