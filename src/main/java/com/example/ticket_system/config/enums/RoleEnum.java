package com.example.ticket_system.config.enums;

public enum RoleEnum {
    USER("user", "普通用户"),
    MERCHANT("merchant", "商家"),
    ADMIN("admin", "管理员");

    private final String code;
    private final String desc;

    RoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
