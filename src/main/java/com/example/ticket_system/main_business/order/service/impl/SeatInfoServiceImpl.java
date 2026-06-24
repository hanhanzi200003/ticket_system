package com.example.ticket_system.main_business.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.ticket_system.main_business.event.entity.SeatInfo;
import com.example.ticket_system.main_business.event.mapper.SeatInfoMapper;
import com.example.ticket_system.main_business.order.service.SeatInfoService;
import com.example.ticket_system.main_business.order.service.SeatService;
import com.example.ticket_system.main_business.order.vo.SeatLockResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SeatInfo 座位记录服务实现
 * <p>
 * MySQL SeatInfo 作为最终数据源，仅用于 Redis 数据丢失后恢复。
 * status 只有两种：0=可售, 1=已售（含锁定）。
 * <ul>
 *   <li>下单 MQ 落库 → 已售(1)（Redis 锁定后异步更新 MySQL）</li>
 *   <li>支付成功 → 无需更新（锁定时已是 1）</li>
 *   <li>取消订单 → 可售(0) + 归还 Redis</li>
 * </ul>
 */
@Slf4j
@Service
public class SeatInfoServiceImpl implements SeatInfoService {

    @Autowired
    private SeatInfoMapper seatInfoMapper;

    @Autowired
    private SeatService seatService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchLockSeats(Long concertId, Long tierId, List<String> seatLabels) {
        if (seatLabels == null || seatLabels.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (String seatNo : seatLabels) {
            int updated = seatInfoMapper.update(null, new LambdaUpdateWrapper<SeatInfo>()
                    .eq(SeatInfo::getConcertId, concertId)
                    .eq(SeatInfo::getTierId, tierId)
                    .eq(SeatInfo::getSeatNo, seatNo)
                    .eq(SeatInfo::getStatus, 0) // 仅可售→已售
                    .set(SeatInfo::getStatus, 1)
                    .set(SeatInfo::getUpdateTime, now));
            if (updated > 0) {
                count++;
            }
        }

        log.info("SeatInfo 标记已售完成：concertId={}, tierId={}, sold={}/{}",
                concertId, tierId, count, seatLabels.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeatLockResultVO lockSeatsFromDb(Long concertId, Long tierId, int quantity) {
        // 1. SELECT ... FOR UPDATE 锁定可售座位行（防止并发抢占）
        QueryWrapper<SeatInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("concert_id", concertId)
                .eq("tier_id", tierId)
                .eq("status", 0)
                .last("LIMIT " + quantity + " FOR UPDATE");

        List<SeatInfo> availableSeats = seatInfoMapper.selectList(queryWrapper);

        if (availableSeats.size() < quantity) {
            log.warn("[DB降级锁座] 座位不足：concertId={}, tierId={}, 需要={}, 可用={}",
                    concertId, tierId, quantity, availableSeats.size());
            return new SeatLockResultVO(false, null,
                    "座位不足，需要" + quantity + "个，可用" + availableSeats.size() + "个");
        }

        // 2. 收集座位编号
        List<String> seatLabels = availableSeats.stream()
                .map(SeatInfo::getSeatNo)
                .collect(Collectors.toList());

        // 3. 批量更新 status 0→1
        LocalDateTime now = LocalDateTime.now();
        int updated = seatInfoMapper.update(null, new LambdaUpdateWrapper<SeatInfo>()
                .eq(SeatInfo::getConcertId, concertId)
                .eq(SeatInfo::getTierId, tierId)
                .in(SeatInfo::getSeatNo, seatLabels)
                .eq(SeatInfo::getStatus, 0)
                .set(SeatInfo::getStatus, 1)
                .set(SeatInfo::getUpdateTime, now));

        if (updated < quantity) {
            log.error("[DB降级锁座] 并发冲突，更新行数不足：expected={}, actual={}", quantity, updated);
            return new SeatLockResultVO(false, null, "锁座失败，请重试");
        }

        log.info("[DB降级锁座] 成功：concertId={}, tierId={}, locked={}", concertId, tierId, seatLabels);
        return new SeatLockResultVO(true, seatLabels, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchReleaseSeats(Long concertId, Long tierId, List<String> seatLabels) {
        if (seatLabels == null || seatLabels.isEmpty()) {
            return;
        }

        // 1. 归还到 Redis 可用池（Redis 熔断时内部会自动跳过）
        seatService.releaseSeats(concertId, tierId, seatLabels);

        // 2. 更新 MySQL SeatInfo 为可售(0)
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (String seatNo : seatLabels) {
            int updated = seatInfoMapper.update(null, new LambdaUpdateWrapper<SeatInfo>()
                    .eq(SeatInfo::getConcertId, concertId)
                    .eq(SeatInfo::getTierId, tierId)
                    .eq(SeatInfo::getSeatNo, seatNo)
                    .eq(SeatInfo::getStatus, 1) // 已售→可售
                    .set(SeatInfo::getStatus, 0)
                    .set(SeatInfo::getOrderNo, null)
                    .set(SeatInfo::getUpdateTime, now));
            if (updated > 0) {
                count++;
            }
        }

        log.info("SeatInfo 释放完成：concertId={}, tierId={}, released={}/{}",
                concertId, tierId, count, seatLabels.size());
    }
}
