package com.example.ticket_system.main_business.order.service;

import com.example.ticket_system.main_business.order.vo.SeatLockResultVO;

import java.util.List;

/**
 * 座位服务接口（基于 Redis）
 */
public interface SeatService {

    /**
     * 初始化演唱会某票档的座位到 Redis
     *
     * @param concertId  演唱会ID
     * @param tierId     票档ID
     * @param areaName   区域名称（如 A区、B区）
     * @param totalStock 总座位数
     */
    void initSeats(Long concertId, Long tierId, String areaName, int totalStock);

    /**
     * 从 Redis 锁定指定数量的座位
     *
     * @param concertId 演唱会ID
     * @param tierId    票档ID
     * @param quantity  需要的数量
     * @param orderNo   订单号（锁定到谁名下）
     * @return 锁定结果（成功则包含座位列表，失败则返回 null）
     */
    SeatLockResultVO lockSeats(Long concertId, Long tierId, int quantity, String orderNo);

    /**
     * 释放座位（取消订单/退款时归还到可用池）
     *
     * @param concertId 演唱会ID
     * @param tierId    票档ID
     * @param seatLabels 要释放的座位标签列表
     */
    void releaseSeats(Long concertId, Long tierId, List<String> seatLabels);

    /**
     * 获取某票档的可用座位数
     */
    long getAvailableCount(Long concertId, Long tierId);

    /**
     * 删除演唱会所有座位数据（演唱会下架/删除时清理）
     */
    void deleteSeats(Long concertId, Long tierId);

    /**
     * 从 MySQL 重建所有可售座位到 Redis
     * <p>
     * Redis 恢复后调用，查询 SeatInfo 表中 status=0 的座位，
     * 按 concertId+tierId 分组后重建 Redis 座位池。
     */
    void restoreSeatsFromDb();
}