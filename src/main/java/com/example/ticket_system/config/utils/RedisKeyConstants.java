package com.example.ticket_system.config.utils;

/**
 * Redis Key 常量类
 */
public class RedisKeyConstants {
    
    // 演唱会缓存前缀
    public static final String CONCERT_PREFIX = "concert:";
    
    // 演唱会基本信息 Hash Key
    // concert:info:{concertId}
    public static final String CONCERT_INFO_KEY = CONCERT_PREFIX + "info:%d";
    
    // 演唱会票档列表 Key
    // concert:tiers:{concertId}
    public static final String CONCERT_TIERS_KEY = CONCERT_PREFIX + "tiers:%d";
    
    // 所有上架演唱会ID集合（用于列表查询）
    // concert:online:ids
    public static final String CONCERT_ONLINE_IDS = CONCERT_PREFIX + "online:ids";

    // ========== 座位相关 ==========

    // 演唱会某票档的可用座位池 (Set)
    // concert:seats:{concertId}:{tierId}:available
    public static final String CONCERT_SEATS_AVAILABLE = CONCERT_PREFIX + "seats:%d:%d:available";

    // 演唱会某票档的已锁定座位 (Hash: seatLabel -> orderNo)
    // concert:seats:{concertId}:{tierId}:locked
    public static final String CONCERT_SEATS_LOCKED = CONCERT_PREFIX + "seats:%d:%d:locked";
}
