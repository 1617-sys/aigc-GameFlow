# 高并发核心版实施结果

完成日期：2026-08-05

## 完成项

- 删除 Agent、Neo4j、聊天页面、OpenAI Provider 及其无关依赖
- 幂等提交、请求哈希、数据库唯一约束、原子扣费
- Redis Lua 双层限流和任务缓存
- RabbitMQ Publisher Confirm、并发消费、手动 ACK、延迟重试、DLQ
- 任务状态 CAS、取消与迟到结果竞态保护
- 免费 Mock Provider，Docker 环境禁止误用付费接口
- Nginx 双应用实例、MinIO、JMeter 脚本和数据库迁移脚本

## 已验证

- Maven 单元测试：6/6 通过
- 真实 MySQL、Redis、RabbitMQ、MinIO 联调通过
- 同一用户、同一幂等键、同一请求连续提交：返回同一个任务 UUID，余额从 10 变为 9
- 同一幂等键提交不同请求：HTTP 409
- Mock 成功链路：任务最终为 SUCCESS 且存在 MinIO 图片 URL
- Mock 强制失败链路：完成一次延迟重试，最终 FAILED，事件包含 `TASK_DEAD_LETTERED`，RabbitMQ `generation.dlq` 中存在消息
- `docker compose config --quiet` 通过

## 尚未伪造的指标

仓库提供 JMeter 脚本，但没有把当前开发机的一次临时运行包装成简历吞吐数据。正式面试数字应在固定环境执行 60 秒以上测试后填写 RPS、P95、P99 和错误率。

本地完整镜像构建验证曾遇到 Docker Hub HTTP 429 拉取限流；这属于镜像仓库限制，不是 Compose 语法或 Java 构建错误。Maven 本地构建与中间件真实联调已完成。

## V4 可靠性加固

- 余额扣减、任务创建与 Outbox 事件在同一 MySQL 本地事务提交，Relay 使用数据库抢占租约、Publisher Confirm 和指数退避完成可靠投递。
- RUNNING 任务记录 Worker、租约截止时间和心跳；Worker 中断后由恢复任务 CAS 转入重试，超过重试上限则失败收敛。
- 迟到的 Worker 结果只有在仍持有有效租约时才能落库，避免恢复后的新执行结果被旧 Worker 覆盖。
- 新增 Testcontainers 故障测试，覆盖并发重复提交、事务后 Outbox 投递、执行租约过期恢复、重试上限和 RabbitMQ 中断。

## V3 高负载加固

- 新增 24 小时 Redis 幂等结果缓存；缓存未命中时先限流、检查队列水位，再访问 MySQL。
- 新增 RabbitMQ 主队列与重试队列每秒采样，总积压默认达到 5,000 时返回 HTTP 503。
- RabbitMQ 状态不可读取时失败关闭，拒绝继续扣费创建新任务。
- 前端自动轮询改为最多 20 条任务的批量状态接口，服务端使用 Redis MGET，未命中时仅执行一次 MySQL 批量查询。
- Tomcat、HikariCP、Redis 连接池参数显式化。
- JMeter 改为多用户 Token CSV，新增压测账号和余额准备脚本。
- 自动化测试增加到 10 个并全部通过。
- 真实联调验证：重复提交返回同一任务且只扣一次余额；积压阈值设为 0 时返回 503、`Retry-After: 5` 且余额不变。
