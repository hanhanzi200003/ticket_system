package com.example.ticket_system.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 *
 * 交换机：
 *   - order.exchange（Direct）：用于常规订单消息（持久化/取消持久化）
 *   - order.dlx（Direct）：全局死信交换机，用于：
 *       ① 延迟消息 TTL 过期转发（order.delay → order.cancel）
 *       ② 业务队列重试耗尽后转发 DLQ（order.persist → order.persist.dlq）
 *
 * 重试机制：
 *   - 生产者：spring.rabbitmq.template.retry.*（MQ不可达时重试3次）
 *   - 消费者：spring.rabbitmq.listener.simple.retry.*（业务失败时重试3次）
 *   - 重试耗尽后 → 消息进入 DLQ，防止丢失，供人工介入补偿
 */
@Configuration
public class RabbitMQConfig {

    /** 延迟队列 TTL（毫秒）：10 分钟 */
    private static final long DELAY_TTL = 10 * 60 * 1000L;

    // ==================== 订单交换机（Direct） ====================

    @Bean
    public DirectExchange orderExchange() {
        return ExchangeBuilder.directExchange("order.exchange")
                .durable(true)
                .build();
    }

    // ==================== 持久化队列（Redis锁座 → 异步落库） ====================

    @Bean
    public Queue orderPersistQueue() {
        return QueueBuilder.durable("order.persist.queue")
                // 消费者重试耗尽后进入 DLQ
                .deadLetterExchange("order.dlx")
                .deadLetterRoutingKey("order.persist.dlq")
                .build();
    }

    @Bean
    public Binding orderPersistBinding() {
        return BindingBuilder.bind(orderPersistQueue())
                .to(orderExchange())
                .with("order.persist");
    }

    // ========== 持久化 DLQ（重试耗尽后兜底） ==========

    @Bean
    public Queue orderPersistDlq() {
        return QueueBuilder.durable("order.persist.dlq")
                .build();
    }

    @Bean
    public Binding orderPersistDlqBinding() {
        return BindingBuilder.bind(orderPersistDlq())
                .to(orderDlx())
                .with("order.persist.dlq");
    }

    // ==================== 取消持久化队列（Redis释放座位 → 异步更新数据库） ====================

    @Bean
    public Queue orderCancelPersistQueue() {
        return QueueBuilder.durable("order.cancel-persist.queue")
                // 消费者重试耗尽后进入 DLQ
                .deadLetterExchange("order.dlx")
                .deadLetterRoutingKey("order.cancel-persist.dlq")
                .build();
    }

    @Bean
    public Binding orderCancelPersistBinding() {
        return BindingBuilder.bind(orderCancelPersistQueue())
                .to(orderExchange())
                .with("order.cancel-persist");
    }

    // ========== 取消持久化 DLQ（重试耗尽后兜底） ==========

    @Bean
    public Queue orderCancelPersistDlq() {
        return QueueBuilder.durable("order.cancel-persist.dlq")
                .build();
    }

    @Bean
    public Binding orderCancelPersistDlqBinding() {
        return BindingBuilder.bind(orderCancelPersistDlq())
                .to(orderDlx())
                .with("order.cancel-persist.dlq");
    }

    // ==================== 全局死信交换机（Direct） ====================

    @Bean
    public DirectExchange orderDlx() {
        return ExchangeBuilder.directExchange("order.dlx")
                .durable(true)
                .build();
    }

    // ========== 延迟队列（消息在此等待过期） ==========

    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable("order.delay.queue")
                // 消息在此队列存活10分钟后过期
                .ttl((int) DELAY_TTL)
                // 过期后通过死信交换机转发
                .deadLetterExchange("order.dlx")
                // 转发时使用的 routing key
                .deadLetterRoutingKey("order.cancel")
                .build();
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderDlx())
                .with("order.delay");
    }

    // ========== 超时取消队列（消费者监听此队列） ==========

    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable("order.cancel.queue")
                .build();
    }

    @Bean
    public Binding orderCancelBinding() {
        return BindingBuilder.bind(orderCancelQueue())
                .to(orderDlx())
                .with("order.cancel");
    }

    // ==================== 取消事件队列（统一任务消费） ====================

    @Bean
    public Queue orderCancelEventQueue() {
        return QueueBuilder.durable("order.cancel.event.queue")
                .build();
    }

    @Bean
    public Binding orderCancelEventBinding() {
        return BindingBuilder.bind(orderCancelEventQueue())
                .to(orderExchange())
                .with("order.cancel.event");
    }

    // ==================== 批量退款任务事件队列 ====================

    @Bean
    public Queue orderRefundJobEventQueue() {
        return QueueBuilder.durable("order.refund-job.event.queue")
                .build();
    }

    @Bean
    public Binding orderRefundJobEventBinding() {
        return BindingBuilder.bind(orderRefundJobEventQueue())
                .to(orderExchange())
                .with("order.refund-job.event");
    }
}