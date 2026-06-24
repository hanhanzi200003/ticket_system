# Ticket System - 高性能在线票务系统

基于 Spring Boot 3 构建的生产级演唱会购票平台，支持高并发抢票、多角色管理与异步履约。

## 目录

- [项目简介](#项目简介)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [核心功能](#核心功能)
- [高并发设计](#高并发设计)
- [数据库设计](#数据库设计)
- [API 概览](#api-概览)
- [快速启动](#快速启动)
- [项目结构](#项目结构)

## 项目简介

Ticket System 是一个面向高并发场景的在线票务系统，覆盖演唱会购票的完整业务流程。系统包含用户端（浏览、购票、支付）、商家端（发布、管理演唱会）和管理端（审核、用户管理）三大模块。

核心难点在于库存一致性与高并发的平衡。本系统采用 Redis Lua 原子锁座 + MQ 异步落库的架构，在保证不超卖的前提下，支撑万级 TPS 的抢票场景。

## 技术栈

| 类别 | 技术 | 说明 |
|------|------|------|
| 核心框架 | Spring Boot 3.5 / Java 17 | 应用主框架 |
| 安全框架 | Spring Security 6 + JWT | 认证授权、Token 鉴权 |
| ORM | MyBatis-Plus 3.5.5 | 持久层框架 |
| 数据库 | MySQL 8.0 | 关系型数据库 |
| 缓存 | Redis 7 | 座位池、限流、分布式锁、幂等 |
| 消息队列 | RabbitMQ | 异步落库、延迟取消、削峰填谷 |
| 对象存储 | MinIO | 演唱会封面、详情图存储 |
| 支付网关 | 策略模式 (Mock / 微信 / 支付宝) | 可切换支付平台 |
| 其他 | Lua / Thumbnailator / JJWT / Lombok | 原子脚本、图片处理、Token |

## 系统架构

### 请求处理链路

```
RequestValidationFilter  (请求安全校验: SQL注入、XSS、路径穿越)
       |
RateLimitFilter          (Redis 滑动窗口限流: 每用户每秒5次)
       |
TokenFilter              (JWT 登录认证 + 角色鉴权: user/merchant/admin)
       |
IdempotencyFilter        (写操作幂等性校验: X-Idempotency-Key)
       |
Controller 层             (演唱会、订单、支付、管理后台)
       |
Service 层                (业务逻辑)
       |
   ----+----+----+----
   |    |    |    |
  MySQL Redis RabbitMQ MinIO
```

### 下单核心流程

```
步骤1: 参数校验 + 业务校验
       演唱会是否存在、审核是否通过、是否在售票期内
       (MySQL 只读，无锁)

步骤2: Redis Lua 原子锁座
       SCARD 检查库存 -> SPOP 弹出座位 -> HSET 标记锁定
       (纯内存操作，核心性能点)

步骤3: 本地事务写入
       INSERT order_info + INSERT mq_message (同一事务)
       (Transactional Outbox 保证一致性)

步骤4: 事务外发送 MQ
       成功 -> 标记已发送; 失败 -> 定时任务补发

步骤5: MQ 延迟消息 (TTL 10分钟)
       order.delay.queue -> order.dlx -> order.cancel.queue
       (超时自动取消)

步骤6: 定时任务兜底扫描 (每60秒)
       OrderTimeoutScanner 扫描超时未支付订单
       直接释放 Redis 座位 (防止 MQ 消息丢失)
```

### 过滤器执行链

| 顺序 | 过滤器 | 职责 |
|------|--------|------|
| 1 | RequestValidationFilter | 请求安全校验：SQL注入、XSS、路径穿越、请求体大小限制 |
| 2 | RateLimitFilter | Redis 滑动窗口限流，每用户每秒最多 5 个请求 |
| 3 | TokenFilter | JWT 登录认证、角色鉴权 (user / merchant / admin) |
| 4 | IdempotencyFilter | 写操作幂等性校验，基于 X-Idempotency-Key |

## 核心功能

### 用户端

- 演唱会浏览：分页列表、按城市筛选、查看详情（含票档、座位、场馆信息）
- 选座购票：选择票档和数量，实时库存校验
- 订单支付：准备支付 -> 调用支付网关 -> 回调处理，完整状态机流转
- 订单管理：查看订单列表与详情、取消订单、逻辑删除
- 超时保护：下单后 10 分钟未支付自动取消，Redis 座位自动释放

### 商家端

- 演唱会管理：发布草稿、提交审核、编辑信息、替换图片
- 演唱会取消：一键取消并触发批量退款流程（MQ 异步处理 + 定时兜底）

### 管理端

- 演唱会审核：审核通过、拒绝商家提交的演唱会
- 用户管理：禁用、启用用户账号

### 技术特性

- Redis 熔断降级：连续失败 5 次自动熔断 -> 30s 后半开探活 -> 渐进恢复（10% -> 30% -> 50% -> 100%）
- Redis 自我修复：恢复后自动从 MySQL 重建座位池缓存
- Transactional Outbox：本地消息表保证 MQ 消息与数据库事务的最终一致性
- 可切换支付网关：策略模式，`pay.channel=mock|wechat|alipay` 一键切换
- 三段状态模型：演唱会实体使用 lifecycle_status、audit_status、operation_status 三个独立状态字段
- 订单快照：下单时对演唱会信息（名称、艺人、票价、场馆等）做快照，防止后续修改影响历史订单
- 分布式 ID：雪花算法生成全局唯一订单 ID
- 图片处理：自动压缩、2:3 比例中心裁剪

## 高并发设计

### 性能目标

| 指标 | 设计值 | 支撑依据 |
|------|--------|----------|
| 峰值下单 TPS | 10,000 - 20,000 | Redis Lua 原子锁座（纯内存，约 5us/次） |
| 同时在线 | 3,000 - 5,000 | 每用户限流 5 QPS，总入口 QPS 可控 |
| 日订单量 | 数百万 | MQ 削峰 + MySQL 批量写入 |

### 核心策略

1. 读写分离：Redis 处理写热点（锁座），MySQL 作为最终数据源
2. 同步转异步：锁座同步执行（强一致性），订单落库异步执行（最终一致性）
3. 削峰填谷：请求经过 MQ 缓冲，DB 平稳写入
4. 双保险机制：MQ 延迟消息 + 定时任务双重保障超时取消
5. 熔断自愈：Redis 故障时自动降级到 DB 锁座，恢复后自动重建缓存
6. 幂等防护：全局幂等性校验防止重复下单、支付

### Redis 熔断器状态机

```
CLOSED (正常)
  |  连续失败 >= 5 次
  v
OPEN (熔断) -- 30秒后 --> HALF_OPEN (半开探测)
                              |
          +-------------------+-------------------+
          |                   |                   |
          v                   v                   v
  探活连续成功>=2次       探活失败            用户请求全部降级
          |                   |
          v                   v
    RECOVERING          OPEN (重新熔断)
    (渐进恢复)
    10% -> 30% -> 50% -> 100%
          |
          v
    CLOSED (恢复完成，触发数据重建回调)
```

## 数据库设计

### 主要表结构

| 表名 | 说明 | 核心字段 |
|------|------|----------|
| concert | 演唱会 | lifecycle_status, audit_status, operation_status |
| ticket_tier | 票档 | 关联 concert, 价格, 总库存, 可用库存 |
| seat_info | 座位明细 | concert_id, tier_id, seat_no, status (0可售/1已售) |
| order_info | 订单 | 状态 (0待支付/1支付中/2已支付/3已取消/4已退款), 快照字段 |
| order_cancel_task | 取消任务 | 异步处理取消逻辑 |
| refund_task | 退款任务 | 退款状态追踪 |
| concert_refund_job | 批量退款任务 | 商家取消演唱会时的批量退款 |
| mq_message | 本地消息表 | Transactional Outbox 模式 |
| user | 用户 | 角色 (user/merchant/admin), 状态 |

### 订单状态机

```
创建订单 -> 待支付(0)
              |
              +-- 进入支付 -> 支付中(1) -> 支付成功 -> 已支付(2)
              |                    |
              |                    +-- 支付超时 -> 已取消(3)
              |
              +-- 超时未支付 -> 已取消(3)
              |
              +-- 用户取消 -> 已取消(3)

已支付(2) -> 退款 -> 已退款(4)
```

## API 概览

### 公开接口（无需登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /auth/login | 用户登录 |
| POST | /auth/regester | 用户注册 |
| GET | /concert/list | 演唱会列表（分页、城市筛选） |
| GET | /concert/{id} | 演唱会详情 |

### 用户接口（需登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /order/create | 创建订单 |
| POST | /order/cancel | 取消订单 |
| POST | /order/delete | 逻辑删除订单 |
| POST | /order/prepare-pay | 支付准备 |
| POST | /order/pay | 支付订单 |
| GET | /order/{id} | 订单详情 |
| GET | /order/list | 订单列表 |

### 商家接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /merchant/concert/publish | 发布演唱会（草稿） |
| POST | /merchant/concert/submit/{id} | 提交审核 |
| PUT | /merchant/concert/update | 更新演唱会 |
| PUT | /merchant/concert/images/replace | 替换图片 |
| DELETE | /merchant/concert/{id} | 删除草稿 |
| POST | /merchant/concert/{id}/cancel | 取消演唱会 |
| GET | /merchant/concert/list | 演唱会列表 |
| GET | /merchant/concert/drafts | 草稿列表 |

### 管理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /admin/concert/audit | 审核演唱会 |
| GET | /admin/concert/pending | 待审核列表 |
| GET | /admin/user/list | 用户列表 |
| POST | /admin/user/{id}/toggle-status | 切换用户状态 |

## 快速启动

### 环境要求

- JDK 17+
- MySQL 8.0+
- Redis 7+
- RabbitMQ 3.12+
- MinIO（可选，图片存储）

### 步骤

1. 启动基础设施

```bash
# MySQL
docker run -d --name mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=your_password \
  -e MYSQL_DATABASE=ticket_system mysql:8.0

# Redis
docker run -d --name redis -p 6379:6379 redis:7

# RabbitMQ
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 \
  rabbitmq:3.12-management

# MinIO（可选）
docker run -d --name minio -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=admin \
  -e MINIO_ROOT_PASSWORD=your_password \
  minio/minio server /data --console-address ":9001"
```

2. 创建数据库表

执行 `docs/sql/init.sql`（或根据实体类自动建表）。

3. 配置并启动应用

```bash
git clone https://github.com/your-username/ticket-system.git
cd ticket-system

# 修改 src/main/resources/application.properties 中的连接信息

./mvnw spring-boot:run
```

4. 访问系统

- 首页: http://localhost:8080/
- 用户端: http://localhost:8080/index.html
- 商家端: http://localhost:8080/merchant.html
- 管理端: http://localhost:8080/admin.html

## 项目结构

```
ticket-system/
|-- src/main/java/com/example/ticket_system/
|   |-- TicketSystemApplication.java          # 启动类
|   |-- config/                                # 全局配置
|   |   |-- SecurityConfig.java                # 安全配置 + 过滤器链
|   |   |-- RedisConfig.java                   # Redis 序列化配置
|   |   |-- RabbitMQConfig.java                # 消息队列配置
|   |   |-- MinioConfig.java                   # MinIO 客户端配置
|   |   |-- WebConfig.java                     # Web MVC 配置
|   |   |-- enums/RoleEnum.java                # 角色枚举
|   |   |-- exception/                         # 全局异常处理
|   |   |-- filter/                            # 安全过滤器
|   |   |   |-- RequestValidationFilter.java   # 请求安全校验
|   |   |   |-- RateLimitFilter.java           # Redis 限流
|   |   |   |-- TokenFilter.java               # JWT 鉴权
|   |   |   |-- IdempotencyFilter.java         # 幂等性校验
|   |   |-- utils/                             # 工具类
|   |       |-- RedisUtil.java                 # Redis 操作 + 熔断器
|   |       |-- RabbitMQUtil.java              # MQ 操作
|   |       |-- SnowflakeIdGenerator.java      # 雪花算法 ID
|   |       |-- TokenUtil.java                 # JWT 工具
|   |       |-- RedisKeyConstants.java         # Redis Key 常量
|   |       |-- ImageCompressor.java           # 图片压缩
|   |       |-- ImageCropper.java              # 图片裁剪
|   |       |-- MinioUtil.java                 # MinIO 操作
|   |-- login/                                 # 登录模块
|   |-- main_business/
|   |   |-- event/                             # 演唱会模块
|   |   |   |-- controller/
|   |   |   |-- service/
|   |   |   |-- entity/ (Concert, TicketTier, SeatInfo, Artist)
|   |   |   |-- mapper/
|   |   |   |-- dto/vo/
|   |   |-- order/                             # 订单模块（核心）
|   |   |   |-- controller/OrderController.java
|   |   |   |-- service/impl/OrderServiceImpl.java
|   |   |   |-- service/impl/SeatServiceImpl.java
|   |   |   |-- mq/                            # MQ 生产者/消费者
|   |   |   |-- schedule/                      # 定时任务
|   |   |   |-- entity/dto/vo/
|   |   |-- pay/                               # 支付模块
|   |       |-- gateway/                       # 支付网关接口 + 工厂
|   |       |-- gateway/mock/MockPayGateway.java
|   |       |-- controller/
|   |-- transactional_outbox/                  # 本地消息表
|       |-- entity/MqMessage.java
|       |-- service/impl/MqMessageServiceImpl.java
|       |-- schedule/MqMessageResendTask.java
|-- src/main/resources/
|   |-- application.properties                 # 应用配置
|   |-- lua/                                   # Redis Lua 脚本
|   |   |-- lockSeats.lua                      # 原子锁座
|   |   |-- releaseSeats.lua                   # 原子释放座位
|   |-- static/                                # 前端页面
|       |-- index.html
|       |-- login.html
|       |-- detail.html
|       |-- pay.html
|       |-- orders.html
|       |-- merchant.html
|       |-- admin.html
|-- pom.xml
```

## 设计亮点

### 1. Redis 熔断降级与自愈

Redis 不可用时不会直接崩溃，而是：

1. 熔断：连续 5 次失败后自动熔断，后续请求走数据库降级
2. 探活：后台线程每 2 秒 ping 一次
3. 渐进恢复：恢复后按 10% -> 30% -> 50% -> 100% 逐步放量
4. 数据重建：完全恢复后自动从 MySQL 重建 Redis 缓存

### 2. Transactional Outbox 模式

通过本地消息表保证 MQ 消息不丢失：

- 订单写入和消息写入在同一数据库事务中
- 消息发送失败由 MqMessageResendTask（每秒扫描）自动补发
- 超过最大重试次数（3次）的消息标记为失败，人工介入处理

### 3. Lua 原子锁座

一次 Redis 调用完成：检查库存 + 弹出座位 + 标记锁定，避免并发场景下的超卖问题。

```lua
local available = redis.call('SCARD', KEYS[1])
if available < quantity then return {"0", available} end
local seats = redis.call('SPOP', KEYS[1], quantity)
for _, seat in ipairs(seats) do
    redis.call('HSET', KEYS[2], seat, orderNo)
end
```

### 4. 三段状态模型

演唱会实体使用三个独立状态字段，避免单一状态爆炸：

- lifecycle_status：业务生命周期（草稿 -> 待上架 -> 售票中 -> 已结束 -> 已下架）
- audit_status：审核流程（待审核 -> 通过 -> 拒绝）
- operation_status：操作流程（无操作 -> 取消申请中 -> 退款处理中 -> 已完成）

## License

MIT