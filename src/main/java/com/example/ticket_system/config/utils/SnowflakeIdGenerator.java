package com.example.ticket_system.config.utils;

import org.springframework.stereotype.Component;

/**
 * 雪花算法工具类
 * 用于生成分布式唯一ID
 */
@Component
public class SnowflakeIdGenerator {

    // 起始时间戳（2024-01-01 00:00:00）
    private static final long START_TIMESTAMP = 1704067200000L;

    // 机器ID所占位数
    private static final long MACHINE_ID_BITS = 5L;
    // 数据中心ID所占位数
    private static final long DATA_CENTER_ID_BITS = 5L;

    // 序列号所占位数
    private static final long SEQUENCE_BITS = 12L;

    // 机器ID最大值
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);
    // 数据中心ID最大值
    private static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);
    // 序列号最大值
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    // 各部分左移位数
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATA_CENTER_ID_BITS;

    private long dataCenterId;  // 数据中心ID
    private long machineId;     // 机器ID
    private long sequence = 0L; // 序列号
    private long lastTimestamp = -1L; // 上次生成ID的时间戳

    /**
     * 构造函数
     * @param dataCenterId 数据中心ID (0-31)
     * @param machineId 机器ID (0-31)
     */
    public SnowflakeIdGenerator(long dataCenterId, long machineId) {
        if (dataCenterId > MAX_DATA_CENTER_ID || dataCenterId < 0) {
            throw new IllegalArgumentException("数据中心ID必须在0-" + MAX_DATA_CENTER_ID + "之间");
        }
        if (machineId > MAX_MACHINE_ID || machineId < 0) {
            throw new IllegalArgumentException("机器ID必须在0-" + MAX_MACHINE_ID + "之间");
        }
        this.dataCenterId = dataCenterId;
        this.machineId = machineId;
    }

    /**
     * 默认构造函数，使用数据中心ID=0，机器ID=1
     */
    public SnowflakeIdGenerator() {
        this(0L, 1L);
    }

    /**
     * 生成下一个ID
     * @return 雪花ID
     */
    public synchronized long nextId() {
        long timestamp = getCurrentTimestamp();

        // 如果当前时间小于上一次ID生成的时间戳，说明系统时钟回退过
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回拨，拒绝生成ID");
        }

        // 如果是同一时间生成的，则进行序列号自增
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 序列号溢出处理
            if (sequence == 0) {
                // 阻塞到下一毫秒，获得新的时间戳
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 时间戳改变，序列号重置
            sequence = 0L;
        }

        // 更新上次生成ID的时间戳
        lastTimestamp = timestamp;

        // 生成ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence;
    }

    /**
     * 获取当前时间戳
     * @return 当前时间戳
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 阻塞到下一毫秒
     * @param lastTimestamp 上次生成ID的时间戳
     * @return 下一毫秒的时间戳
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }
}
