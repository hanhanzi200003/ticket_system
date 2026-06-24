package com.example.ticket_system.main_business.event.service;

/**
 * 商家取消演唱会服务
 * <p>
 * 职责：
 * <ol>
 *   <li>校验商家对演唱会的归属权</li>
 *   <li>更新演唱会状态为已取消（下架）</li>
 *   <li>创建 ConcertRefundJob（事务保证）</li>
 *   <li>发送 MQ 消息通知立即执行批量退款</li>
 * </ol>
 * 扫描器每60秒兜底处理未完成的批量退款任务。
 */
public interface MerchantCancelConcertService {

    /**
     * 商家取消演唱会
     *
     * @param concertId  演唱会ID
     * @param merchantId 商家ID（当前登录用户）
     */
    void cancelConcert(Long concertId, Long merchantId);
}