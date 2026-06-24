package com.example.ticket_system.login.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticket_system.login.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper extends BaseMapper<User> {
        // 根据手机号查询用户
        public User findByPhone(@Param("phone") String phone);
        
        // 根据邮箱查询用户
        public User findByEmail(@Param("email") String email);
        
        // 插入用户
        public void createUser(User user);
}
