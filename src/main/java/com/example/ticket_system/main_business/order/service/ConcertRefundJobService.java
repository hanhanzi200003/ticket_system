package com.example.ticket_system.main_business.order.service;

/**
 * 演唱会批量退款任务服务
 * <p>
 * 商家取消演唱会时，创建批量退款任务，分批处理所有订单。
 * 核心逻辑：
 * <ol>
 *   <li>创建 ConcertRefundJob（统计总订单数）</li>
 *   <li>分批扫描订单，last_order_id 断点续传</li>
 *   <li>已支付(2)→创建 RefundTask；未支付(0/1)→创建 CancelTask</li>
 *   <li>全部处理完毕 → 标记 job 完成</li>
 * </ol>
 */
public interface ConcertRefundJobService {

    /**
     * 创建演唱会批量退款任务
     *
     * @param concertId 演唱会ID
     * @return jobId
     */
    Long createJob(Long concertId);

    /**
     * 处理批量退款任务（断点续传）
     * <p>
     * 1. 乐观锁：status=PENDING→PROCESSING
     * 2. 分批查询订单（每批50条，按 order_id 升序）
     * 3. 对每个订单：已支付→创建 RefundTask，未支付→创建 CancelTask
     * 4. 更新 processed_count + last_order_id
     * 5. 未完成 → 继续处理下一批；已完成 → status=COMPLETED
     *
     * @param jobId 任务ID
     */
    void processJob(Long jobId);
}