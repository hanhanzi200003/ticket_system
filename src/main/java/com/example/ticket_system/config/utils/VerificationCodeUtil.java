package com.example.ticket_system.config.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * 商家注册码工具类
 * 验证码存储在后端内存中，不依赖 Redis
 */
@Slf4j
@Component
public class VerificationCodeUtil {

    /** 商家注册码（全局唯一，存储在内存中） */
    private volatile String merchantCode;

    public VerificationCodeUtil() {
        // 初始化时生成一个验证码
        this.merchantCode = generateCode();
        log.info("商家注册码已初始化");
    }

    // 生成6位随机数字的方法
    public String generateCode() {
        Random random = new Random();
        // 生成 100000 到 999999 之间的随机数
        int code = random.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    // 获取当前商家注册码
    public String getMerchantCode() {
        return merchantCode;
    }

    // 刷新商家注册码
    public String refreshMerchantCode() {
        String oldCode = this.merchantCode;
        this.merchantCode = generateCode();
        log.info("商家注册码已刷新");
        return this.merchantCode;
    }

    // 验证商家注册码
    public boolean verifyMerchantCode(String inputCode) {
        return inputCode != null && inputCode.equals(merchantCode);
    }
}
