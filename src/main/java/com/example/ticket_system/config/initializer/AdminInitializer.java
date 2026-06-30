package com.example.ticket_system.config.initializer;

import com.example.ticket_system.config.utils.SnowflakeIdGenerator;
import com.example.ticket_system.login.entity.User;
import com.example.ticket_system.login.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class AdminInitializer implements CommandLineRunner {

    private static final String ADMIN_PHONE = "18929549756";
    private static final String ADMIN_PASSWORD = "hanhanzi";
    private static final String ADMIN_NICKNAME = "管理员";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public void run(String... args) {
        User existingAdmin = userMapper.findByPhone(ADMIN_PHONE);
        if (existingAdmin != null) {
            log.info("[管理员初始化] 管理员账户已存在，跳过创建");
            return;
        }

        User admin = new User();
        admin.setUserId(snowflakeIdGenerator.nextId());
        admin.setPhone(ADMIN_PHONE);
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRole("admin");
        admin.setStatus(1);
        admin.setNickname(ADMIN_NICKNAME);
        admin.setRealNameVerified(0);
        admin.setCreateTime(LocalDateTime.now());

        userMapper.createUser(admin);
        log.info("[管理员初始化] 管理员账户创建成功，手机号={}", ADMIN_PHONE);
    }
}
