package com.example.ticket_system.main_business.order.service;

import com.example.ticket_system.main_business.order.vo.SeatLockResultVO;

import java.util.List;

/**
 * 座位信息服务（SeatInfo MySQL 记录管理）
 * <p>
 * MySQL SeatInfo 作为最终数据源，仅用于 Redis 数据丢失后恢复。
 * status 只有两种：0=可售, 1=已售（含锁定）。
 * Redis 内部区分可用/锁定/已售三种状态，但 MySQL 不区分锁定与已售。
 */
public interface SeatInfoService {

    /**
     * 批量更新座位状态为已售(1)
     * <p>
     * Redis 锁定座位后通过 MQ 异步调用此方法更新 MySQL。
     * MySQL 不区分锁定/已售，统一记为 1，支付成功时无需再更新。
     *
     * @param concertId  演唱会ID
     * @param tierId     票档ID
     * @param seatLabels 座位标签列表（如 A区-0001）
     */
    void batchLockSeats(Long concertId, Long tierId, List<String> seatLabels);

    /**
     * 数据库降级锁座（Redis 熔断时使用）
     * <p>
     * 通过 SELECT ... FOR UPDATE 锁定可售座位行，然后更新 status 0→1。
     * 整个过程在事务中完成，保证原子性。
     *
     * @param concertId 演唱会ID
     * @param tierId    票档ID
     * @param quantity  锁座数量
     * @return 锁座结果
     */
    SeatLockResultVO lockSeatsFromDb(Long concertId, Long tierId, int quantity);

    /**
     * 批量更新座位状态为可售(0)，并重新加入 Redis 可用池
     *
     * @param concertId  演唱会ID
     * @param tierId     票档ID
     * @param seatLabels 座位标签列表
     */
    void batchReleaseSeats(Long concertId, Long tierId, List<String> seatLabels);
}
