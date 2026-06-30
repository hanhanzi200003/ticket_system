-- =============================================
-- Ticket System 建表脚本
-- 数据库: MySQL 8.0+
-- =============================================

-- 如果数据库不存在，先创建
-- CREATE DATABASE IF NOT EXISTS ticket_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
-- USE ticket_system;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. 用户表
-- =============================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `user_id`           BIGINT          NOT NULL                COMMENT '用户ID（雪花算法）',
    `phone`             VARCHAR(20)     DEFAULT NULL            COMMENT '手机号',
    `email`             VARCHAR(100)    DEFAULT NULL            COMMENT '邮箱',
    `password`          VARCHAR(255)    NOT NULL                COMMENT '密码（BCrypt加密）',
    `role`              VARCHAR(20)     NOT NULL DEFAULT 'user' COMMENT '角色：user/merchant/admin',
    `status`            INT             NOT NULL DEFAULT 1      COMMENT '状态：0禁用 1启用',
    `nickname`          VARCHAR(50)     DEFAULT NULL            COMMENT '昵称',
    `avatar_url`        VARCHAR(500)    DEFAULT NULL            COMMENT '头像URL',
    `real_name_verified` INT            NOT NULL DEFAULT 0      COMMENT '实名认证：0未认证 1已认证',
    `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- =============================================
-- 2. 艺人表
-- =============================================
DROP TABLE IF EXISTS `artist`;
CREATE TABLE `artist` (
    `artist_id`     BIGINT          NOT NULL                COMMENT '艺人ID（雪花算法）',
    `name`          VARCHAR(100)    NOT NULL                COMMENT '艺人名称',
    `avatar_url`    VARCHAR(500)    DEFAULT NULL            COMMENT '头像URL',
    `introduction`  TEXT            DEFAULT NULL            COMMENT '简介',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`artist_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='艺人表';

-- =============================================
-- 3. 演唱会表（三段状态模型）
--    lifecycle_status: 0草稿 1待上架 2售票中 3已结束 4已下架
--    audit_status:     0待审核 1通过 2拒绝
--    operation_status: 0无操作 1取消申请中 2退款处理中 3已完成取消流程
-- =============================================
DROP TABLE IF EXISTS `concert`;
CREATE TABLE `concert` (
    `concert_id`            BIGINT          NOT NULL                COMMENT '演唱会ID（雪花算法）',
    `merchant_id`           BIGINT          NOT NULL                COMMENT '商家ID',
    `artist_id`             BIGINT          DEFAULT NULL            COMMENT '艺人ID',
    `name`                  VARCHAR(200)    NOT NULL                COMMENT '演唱会名称',
    `poster_url`            VARCHAR(500)    DEFAULT NULL            COMMENT '海报URL（兼容旧字段）',
    `cover_image`           VARCHAR(500)    DEFAULT NULL            COMMENT '封面图URL（原图）',
    `cover_thumbnail`       VARCHAR(500)    DEFAULT NULL            COMMENT '封面缩略图URL（2:3比例）',
    `detail_images`         TEXT            DEFAULT NULL            COMMENT '详情图JSON数组（最多4张）',
    `city`                  VARCHAR(50)     DEFAULT NULL            COMMENT '城市',
    `venue_name`            VARCHAR(200)    DEFAULT NULL            COMMENT '场馆名称',
    `address`               VARCHAR(500)    DEFAULT NULL            COMMENT '场馆地址',
    `start_time`            DATETIME        DEFAULT NULL            COMMENT '演出开始时间',
    `end_time`              DATETIME        DEFAULT NULL            COMMENT '演出结束时间',
    `sale_start_time`       DATETIME        DEFAULT NULL            COMMENT '售票开始时间',
    `sale_end_time`         DATETIME        DEFAULT NULL            COMMENT '售票结束时间',
    `max_purchase_quantity`  INT            DEFAULT NULL            COMMENT '单次最大购买数量',
    `purchase_notice`       TEXT            DEFAULT NULL            COMMENT '购票须知',
    `description`           TEXT            DEFAULT NULL            COMMENT '演唱会详细介绍',
    `audit_status`          INT             NOT NULL DEFAULT 0      COMMENT '审核状态：0待审核 1通过 2拒绝',
    `audit_remark`          VARCHAR(500)    DEFAULT NULL            COMMENT '审核备注',
    `auditor_id`            BIGINT          DEFAULT NULL            COMMENT '审核人ID',
    `audit_time`            DATETIME        DEFAULT NULL            COMMENT '审核时间',
    `status`                INT             NOT NULL DEFAULT 0      COMMENT '业务生命周期：0草稿 1待上架 2售票中 3已结束 4已下架',
    `operation_status`      INT             NOT NULL DEFAULT 0      COMMENT '操作流程：0无操作 1取消申请中 2退款处理中 3已完成',
    `create_time`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               INT             NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`concert_id`),
    -- 管理员查待审核列表: WHERE audit_status=? AND deleted=? ORDER BY create_time DESC
    KEY `idx_audit_deleted` (`audit_status`, `deleted`),
    -- 商家查草稿/列表: WHERE merchant_id=? AND status=? AND deleted=?
    KEY `idx_merchant_status_deleted` (`merchant_id`, `status`, `deleted`),
    -- 用户查演唱会卡片: WHERE audit_status=? AND deleted=? AND status IN(?) [AND city=?] ORDER BY create_time DESC
    KEY `idx_status_audit_deleted_city` (`status`, `audit_status`, `deleted`, `city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='演唱会表';

-- =============================================
-- 4. 票档表
-- =============================================
DROP TABLE IF EXISTS `ticket_tier`;
CREATE TABLE `ticket_tier` (
    `tier_id`           BIGINT          NOT NULL                COMMENT '票档ID（雪花算法）',
    `concert_id`        BIGINT          NOT NULL                COMMENT '演唱会ID',
    `area_name`         VARCHAR(50)     NOT NULL                COMMENT '区域名称',
    `price`             DECIMAL(10,2)   NOT NULL                COMMENT '单价',
    `total_stock`       INT             NOT NULL                COMMENT '总库存',
    `available_stock`   INT             NOT NULL                COMMENT '可用库存',
    `sort_order`        INT             DEFAULT 0               COMMENT '排序',
    `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`tier_id`),
    KEY `idx_concert_id` (`concert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='票档表';

-- =============================================
-- 5. 座位表（MySQL 作为 Redis 的最终数据源）
--    status: 0可售 1已售（含锁定）
-- =============================================
DROP TABLE IF EXISTS `seat_info`;
CREATE TABLE `seat_info` (
    `seat_id`       BIGINT          NOT NULL                COMMENT '座位ID（雪花算法）',
    `concert_id`    BIGINT          NOT NULL                COMMENT '演唱会ID',
    `tier_id`       BIGINT          NOT NULL                COMMENT '票档ID',
    `seat_no`       VARCHAR(50)     NOT NULL                COMMENT '座位编号（如 A区-0001）',
    `status`        INT             NOT NULL DEFAULT 0      COMMENT '状态：0可售 1已售',
    `order_no`      VARCHAR(30)     DEFAULT NULL            COMMENT '关联订单号',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`seat_id`),
    -- 同一演唱会同一票档下座位号唯一
    UNIQUE KEY `uk_concert_tier_seat` (`concert_id`, `tier_id`, `seat_no`),
    -- DB降级锁座: WHERE concert_id=? AND tier_id=? AND status=0 LIMIT ? FOR UPDATE
    -- Redis恢复重建: WHERE status=0
    -- 批量更新座位状态: WHERE concert_id=? AND tier_id=? AND seat_no IN(?) AND status=?
    KEY `idx_concert_tier_status` (`concert_id`, `tier_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='座位表';

-- =============================================
-- 6. 订单表
--    status: 0待支付 1支付中 2已支付 3已取消 4已退款
-- =============================================
DROP TABLE IF EXISTS `order_info`;
CREATE TABLE `order_info` (
    `order_id`                  BIGINT          NOT NULL                COMMENT '订单ID（雪花算法）',
    `order_no`                  VARCHAR(30)     NOT NULL                COMMENT '订单号（对外展示）',
    `user_id`                   BIGINT          NOT NULL                COMMENT '用户ID',
    `concert_id`                BIGINT          NOT NULL                COMMENT '演唱会ID',
    `tier_id`                   BIGINT          NOT NULL                COMMENT '票档ID',
    `coupon_id`                 BIGINT          DEFAULT NULL            COMMENT '优惠券ID',
    `city`                      VARCHAR(50)     DEFAULT NULL            COMMENT '城市',
    `quantity`                  INT             NOT NULL                COMMENT '购买数量',
    `seats_json`                TEXT            DEFAULT NULL            COMMENT '座位信息JSON数组',
    `original_amount`           DECIMAL(10,2)   NOT NULL                COMMENT '订单原价',
    `actual_amount`             DECIMAL(10,2)   NOT NULL                COMMENT '实付金额',
    `status`                    INT             NOT NULL DEFAULT 0      COMMENT '订单状态：0待支付 1支付中 2已支付 3已取消 4已退款',
    `create_time`               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `pay_time`                  DATETIME        DEFAULT NULL            COMMENT '支付时间',
    `cancel_time`               DATETIME        DEFAULT NULL            COMMENT '取消时间',
    `refund_time`               DATETIME        DEFAULT NULL            COMMENT '退款时间',
    `update_time`               DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `expire_time`               DATETIME        DEFAULT NULL            COMMENT '订单超时时间',
    `user_deleted`              INT             NOT NULL DEFAULT 0      COMMENT '用户删除标记：0未删除 1已删除',
    `pay_transaction_id`        VARCHAR(100)    DEFAULT NULL            COMMENT '支付流水号',
    -- 快照字段
    `snapshot_concert_name`     VARCHAR(200)    DEFAULT NULL            COMMENT '快照-演唱会名称',
    `snapshot_artist_name`      VARCHAR(100)    DEFAULT NULL            COMMENT '快照-艺人名称',
    `snapshot_area_name`        VARCHAR(50)     DEFAULT NULL            COMMENT '快照-座位区域名称',
    `snapshot_ticket_price`     DECIMAL(10,2)   DEFAULT NULL            COMMENT '快照-票单价',
    `snapshot_venue_name`       VARCHAR(200)    DEFAULT NULL            COMMENT '快照-场馆名称',
    `snapshot_city`             VARCHAR(50)     DEFAULT NULL            COMMENT '快照-城市',
    `snapshot_tier_name`        VARCHAR(50)     DEFAULT NULL            COMMENT '快照-票档名称',
    `snapshot_cover_url`        VARCHAR(500)    DEFAULT NULL            COMMENT '快照-封面图URL',
    `snapshot_concert_time`     DATETIME        DEFAULT NULL            COMMENT '快照-演出时间',
    PRIMARY KEY (`order_id`),
    -- 订单号唯一查询（支付回调、MQ消费者等）
    UNIQUE KEY `uk_order_no` (`order_no`),
    -- 用户订单列表: WHERE user_id=? AND user_deleted=? [AND status=?] ORDER BY create_time DESC
    -- 防重复下单: WHERE user_id=? AND status IN(0,1) AND user_deleted=?
    KEY `idx_user_status_deleted` (`user_id`, `status`, `user_deleted`),
    -- 超时扫描: WHERE status IN(0,1) AND expire_time < NOW() ORDER BY create_time ASC
    KEY `idx_expire_status` (`status`, `expire_time`),
    -- 批量退款断点续传: WHERE concert_id=? AND order_id > ? AND status IN(0,1,2) ORDER BY order_id ASC
    KEY `idx_concert_status_order` (`concert_id`, `status`, `order_id`),
    -- Redis补偿扫描: WHERE status IN(3,4) AND update_time > ? ORDER BY update_time
    KEY `idx_status_update` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单表';

-- =============================================
-- 7. 订单取消任务表
--    task_type: 1超时取消 2用户取消 3演唱会取消
--    status: 0待执行 1执行中 2成功 3失败
-- =============================================
DROP TABLE IF EXISTS `order_cancel_task`;
CREATE TABLE `order_cancel_task` (
    `id`                BIGINT          NOT NULL                COMMENT '任务ID（雪花算法）',
    `task_no`           VARCHAR(30)     NOT NULL                COMMENT '任务编号（唯一）',
    `order_id`          BIGINT          NOT NULL                COMMENT '订单ID',
    `user_id`           BIGINT          NOT NULL                COMMENT '用户ID',
    `task_type`         INT             NOT NULL                COMMENT '任务类型：1超时 2用户取消 3演唱会取消',
    `status`            INT             NOT NULL DEFAULT 0      COMMENT '状态：0待执行 1执行中 2成功 3失败',
    `retry_count`       INT             NOT NULL DEFAULT 0      COMMENT '已重试次数',
    `max_retry`         INT             NOT NULL DEFAULT 5      COMMENT '最大重试次数',
    `next_retry_time`   DATETIME        DEFAULT NULL            COMMENT '下次重试时间',
    `payload`           TEXT            DEFAULT NULL            COMMENT '扩展业务数据JSON',
    `execute_time`      DATETIME        DEFAULT NULL            COMMENT '执行时间',
    `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    -- 定时扫描: WHERE status IN(0,3) AND retry_count < ? ORDER BY status ASC, update_time ASC
    KEY `idx_status_retry_update` (`status`, `retry_count`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单取消任务表';

-- =============================================
-- 8. 退款任务表
--    refund_type: 1用户退款 2演唱会取消 3管理员强制退款
--    status: 0待退款 1退款中 2成功 3失败
-- =============================================
DROP TABLE IF EXISTS `refund_task`;
CREATE TABLE `refund_task` (
    `id`                        BIGINT          NOT NULL                COMMENT '任务ID（雪花算法）',
    `refund_no`                 VARCHAR(30)     NOT NULL                COMMENT '退款任务号（幂等键）',
    `order_id`                  BIGINT          NOT NULL                COMMENT '订单ID',
    `user_id`                   BIGINT          NOT NULL                COMMENT '用户ID',
    `concert_id`                BIGINT          NOT NULL                COMMENT '演唱会ID',
    `pay_transaction_id`        VARCHAR(100)    DEFAULT NULL            COMMENT '原支付流水号',
    `refund_amount`             DECIMAL(10,2)   NOT NULL                COMMENT '退款金额',
    `refund_type`               INT             NOT NULL                COMMENT '退款原因：1用户 2演唱会取消 3管理员',
    `status`                    INT             NOT NULL DEFAULT 0      COMMENT '状态：0待退款 1退款中 2成功 3失败',
    `retry_count`               INT             NOT NULL DEFAULT 0      COMMENT '重试次数',
    `max_retry`                 INT             NOT NULL DEFAULT 5      COMMENT '最大重试次数',
    `next_retry_time`           DATETIME        DEFAULT NULL            COMMENT '下次重试时间',
    `refund_transaction_id`     VARCHAR(100)    DEFAULT NULL            COMMENT '第三方退款流水号',
    `fail_reason`               VARCHAR(500)    DEFAULT NULL            COMMENT '失败原因',
    `create_time`               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`               DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    -- 幂等检查: WHERE order_id=? AND refund_type=? ORDER BY create_time DESC LIMIT 1
    KEY `idx_order_refund_type` (`order_id`, `refund_type`),
    -- 定时重试扫描: WHERE status=3 AND retry_count < ? AND (next_retry_time IS NULL OR next_retry_time <= NOW())
    KEY `idx_status_retry_update` (`status`, `retry_count`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='退款任务表';

-- =============================================
-- 9. 演唱会批量退款任务表（断点续传）
--    status: 0待开始 1执行中 2完成
-- =============================================
DROP TABLE IF EXISTS `concert_refund_job`;
CREATE TABLE `concert_refund_job` (
    `job_id`            BIGINT      NOT NULL                COMMENT '任务ID（雪花算法）',
    `concert_id`        BIGINT      NOT NULL                COMMENT '演唱会ID',
    `status`            INT         NOT NULL DEFAULT 0      COMMENT '状态：0待开始 1执行中 2完成',
    `total_count`       INT         NOT NULL DEFAULT 0      COMMENT '总订单数',
    `processed_count`   INT         NOT NULL DEFAULT 0      COMMENT '已处理数',
    `last_order_id`     BIGINT      NOT NULL DEFAULT 0      COMMENT '断点：最后处理的订单ID',
    `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME    DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`job_id`),
    -- 定时扫描: WHERE status IN(0,1) ORDER BY create_time ASC
    -- 幂等检查: WHERE concert_id=? AND status IN(0,1) LIMIT 1
    KEY `idx_concert_status` (`concert_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='演唱会批量退款任务表';

-- =============================================
-- 10. 本地消息表（Transactional Outbox）
--     status: 0待发送 1已发送 2发送失败
-- =============================================
DROP TABLE IF EXISTS `mq_message`;
CREATE TABLE `mq_message` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `message_id`    VARCHAR(64)     NOT NULL                COMMENT '业务幂等ID',
    `event_type`    VARCHAR(50)     NOT NULL                COMMENT '事件类型：ORDER_PERSIST / ORDER_CANCEL_PERSIST',
    `business_id`   BIGINT          NOT NULL                COMMENT '业务ID（如订单ID）',
    `content`       TEXT            NOT NULL                COMMENT '消息体JSON',
    `status`        INT             NOT NULL DEFAULT 0      COMMENT '0待发送 1已发送 2发送失败',
    `retry_count`   INT             NOT NULL DEFAULT 0      COMMENT '已重试次数',
    `error_msg`     VARCHAR(500)    DEFAULT NULL            COMMENT '最近一次错误信息',
    `create_time`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`),
    -- 定时补发扫描: WHERE status=0 AND retry_count < ? ORDER BY create_time ASC
    KEY `idx_status_retry_create` (`status`, `retry_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='本地消息表（Transactional Outbox）';

SET FOREIGN_KEY_CHECKS = 1;