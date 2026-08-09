# AIGC-GameFlow

面向游戏活动高峰的异步 AI 图片任务平台，也是一个聚焦“高并发控制面”的 Java 实习求职项目。高并发发生在任务提交和状态查询，而图片生成由 RabbitMQ 削峰并按下游容量异步执行。

项目已移除 Agent、聊天前端、Neo4j 和 OpenAI 依赖，保留一条可运行、可压测、可讲清楚的核心业务链路。

现在包含一个 Vue 3 任务控制台，提供注册登录、提交任务、状态轮询、结果预览、取消/重试和事件时间线。生产构建由 Nginx 托管，前端与 API 共用 `http://localhost:8080`。

## 已实现的核心能力

- `Idempotency-Key` + 请求哈希 + MySQL 唯一索引，防止重复任务和重复扣费
- 单条条件 SQL 原子扣减余额，避免并发超扣
- Redis Lua 同时执行用户级和全局限流，任务查询使用带随机抖动的缓存
- Redis 幂等结果缓存把重复请求挡在 MySQL 前，批量状态轮询优先使用 Redis MGET
- RabbitMQ 持久化消息、Publisher Confirm、手动 ACK、延迟重试和 DLQ
- 任务、扣费与 Outbox 事件在同一 MySQL 事务提交，Relay 确认投递失败时按指数退避重试
- RabbitMQ 积压水位保护，队列不可用或超过阈值时返回 HTTP 503
- 任务状态 CAS 与 RUNNING 执行租约，重复消息只允许一个消费者执行，Worker 中断后可自动恢复
- Mock、ComfyUI、阿里云百炼万相 Provider；Docker 默认强制使用免费 Mock
- MinIO 统一保存生成结果
- Nginx `least_conn` 负载均衡两个无状态 Spring Boot 实例
- JMeter 提交接口压测脚本

## 请求链路

```text
Client -> Nginx -> JWT -> Redis Lua限流
       -> 幂等校验 -> MySQL事务(原子扣费 + PENDING任务 + Outbox)
       -> Outbox Relay -> RabbitMQ -> Worker CAS与租约抢占 -> Provider -> MinIO
       -> MySQL状态更新 + Redis缓存 -> Client轮询结果
```

## 技术栈

Java 21、Spring Boot 3、Spring Security、MyBatis-Plus、MySQL、Redis、RabbitMQ、MinIO、Nginx、Docker Compose、JUnit 5、Mockito、JMeter。

前端使用 Vue 3 + Vite，不引入组件库、路由或额外状态框架。

## 快速启动

```powershell
Copy-Item .env.template .env
docker compose up -d --build
```

- 统一 API：`http://localhost:8080`
- RabbitMQ 管理台：`http://localhost:15672`（guest / guest）
- MinIO 管理台：`http://localhost:9001`（minioadmin / minioadmin）

前端本地开发：

```powershell
cd frontend
npm install
npm run dev
```

Vite 开发地址为 `http://localhost:5173`，会把 `/user` 和 `/api` 代理到 `http://localhost:8080`。

提交任务必须携带：

```http
POST /api/generation/jobs
Authorization: Bearer <token>
Idempotency-Key: <8至128字符的客户端唯一键>
Content-Type: application/json

{"prompt":"game anniversary poster","preferredProvider":"MOCK"}
```

首次使用已有数据库时依次执行 [scripts/migrate_v2.sql](scripts/migrate_v2.sql) 和 [scripts/migrate_v3_outbox_lease.sql](scripts/migrate_v3_outbox_lease.sql)。全新 Docker 数据卷会自动执行最新 `schema.sql`。

## 文档

- [高并发核心版 PRD](docs/PRD_HIGH_CONCURRENCY_CORE_V2.md)
- [高负载与削峰加固 PRD V3](docs/PRD_HIGH_LOAD_HARDENING_V3.md)
- [架构链路](docs/ARCHITECTURE_FLOW.md)
- [Docker 部署](docs/DOCKER_DEPLOY_GUIDE.md)
- [压测说明](performance/README.md)
- [本次实施结果](docs/IMPLEMENTATION_RESULT.md)

不要在未完成正式压测前把目标 RPS 写成实测数据。当前仓库提供了压测工具和正确性验证，具体吞吐要在目标机器上实测后填写。
