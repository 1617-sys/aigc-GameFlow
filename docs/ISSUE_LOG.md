# AIGC-GameFlow 故障档案

本文档是项目唯一的长期故障记录。后续发现、定位和修复的问题统一追加到这里，不再为单个故障创建零散文档。

## 使用规则

1. 每个问题使用唯一编号：`GF-YYYYMMDD-NN`。
2. 状态只能使用：`待确认`、`排查中`、`已修复`、`已验证`、`暂缓`。
3. 必须记录实际证据，不能只写推测。
4. 修复后必须填写验证方式和结果；没有验证的条目不能标记为“已验证”。
5. 不在文档中记录 API Key、密码、Token 等敏感信息。
6. 同一根因影响多个模块时，保留一个主条目，并在“影响范围”中完整列出。

## 系统中的图片存储位置

图片生成结果不是直接保存在前端，也没有单独的图片数据库。

| 内容 | 存储位置 | 说明 |
| --- | --- | --- |
| 任务、提示词、Provider、状态、错误、图片地址 | MySQL `game_flow.gen_task` | 图片地址字段为 `image_url` |
| 图片原始文件 | MinIO `aigc` Bucket | Mock、万相和 ComfyUI 的最终图片都会统一转存 |
| 生成过程事件 | MySQL `game_flow.generation_event` | 用于前端“执行事件”时间线和故障追踪 |
| 待发布任务 | MySQL `game_flow.generation_outbox` | 事务 Outbox，负责可靠发布到 RabbitMQ |
| 前端展示 | “最近任务”与“生成图库” | 通过受 JWT 保护的图片接口读取，不直接访问 MySQL 或 MinIO |

图片主调用链：

```text
前端提交
  → POST /api/generation/jobs
  → gen_task + generation_outbox 同事务写入
  → Outbox Relay 发布 RabbitMQ
  → Worker 获取任务并选择 Provider
  → Mock / WANX / ComfyUI 生成
  → 图片转存 MinIO
  → gen_task 更新为 SUCCESS 并写入 image_url
  → GET /api/generation/jobs/{taskUuid}/image
  → 前端最近任务与生成图库展示
```

## 问题索引

| 编号 | 日期 | 问题 | 根因 | 状态 |
| --- | --- | --- | --- | --- |
| GF-20260815-01 | 2026-08-15 | Mock 任务实际调用 ComfyUI 并失败 | Provider 静默降级 | 已验证 |
| GF-20260815-02 | 2026-08-15 | Mock 和万相任务长期停留在 PENDING | 应用与 MySQL 时区混用 | 已验证 |
| GF-20260815-03 | 2026-08-15 | 图片生成后无法上传 MinIO | Docker 服务名覆盖本地连接地址 | 已验证 |
| GF-20260815-04 | 2026-08-15 | 图片已有数据但前端加载中断 | Spring Security 拒绝异步分派 | 已验证 |
| GF-20260815-05 | 2026-08-15 | 前端没有明确的图片管理入口 | 仅有任务列表，没有独立图库区域 | 已验证 |
| GF-20260815-06 | 2026-08-15 | 万相 API 请求失败 | API 协议与模型版本不匹配 | 已验证 |
| GF-20260818-01 | 2026-08-18 | 本地后端启动时报 8080 已占用 | 本地与 Docker 共用端口且环境变量覆盖 Profile | 已验证 |

---

## GF-20260815-01：Mock 任务被错误路由到 ComfyUI

- 状态：已验证
- 影响范围：Mock 生成、Provider 下拉列表、失败重试、死信队列
- 用户现象：选择 `MOCK` 后生成失败，错误却指向 `http://127.0.0.1:8000/prompt`。

### 证据

- `gen_task.provider` 记录为 `MOCK`。
- `generation_event` 中的 `PROVIDER_SELECTED` 实际记录为 `COMFYUI`。
- 失败信息为连接 ComfyUI 的 `127.0.0.1:8000` 被拒绝。
- 8 条相关失败消息最终进入 `generation.dlq`。

### 根因

Mock 未启用时，路由器没有拒绝明确指定的 `MOCK`，而是静默选择第一个声称可用的 Provider。ComfyUI 的 `supports()` 又固定返回 `true`，导致任务在用户不知情的情况下被转发到未启动的 ComfyUI。

### 修复

- 显式指定 `preferredProvider` 时严格路由，Provider 不可用则返回明确错误。
- `/api/generation/providers` 只返回当前已配置的 Provider。
- ComfyUI 增加 `COMFYUI_ENABLED` 开关，默认关闭。
- `local` Profile 默认启用 Mock，Docker 环境仍需显式配置。
- 新增 `ImageGenerationRouterTest`，覆盖“不得静默降级”和“仅展示可用 Provider”。

### 验证

- Provider 接口返回 `MOCK,WANX`，不再返回未启用的 ComfyUI。
- Mock 任务的数据库 Provider、事件 Provider 和最终执行 Provider 均为 `MOCK`。
- Mock 端到端任务最终状态为 `SUCCESS`。

### 回归检查

- 关闭 `GENERATION_MOCK_ENABLED` 后，前端不应显示 Mock。
- 明确请求已关闭的 Provider 时，不得调用其他 Provider。
- 启用 ComfyUI 前必须设置 `COMFYUI_ENABLED=true`。

---

## GF-20260815-02：Outbox 任务长期停留在 PENDING

- 状态：已验证
- 影响范围：所有 Provider、RabbitMQ 投递、任务租约和恢复
- 用户现象：前端提交成功，但任务长期处于排队状态，Mock 和万相都没有真正开始调用。

### 证据

- `gen_task` 已创建，状态为 `PENDING`。
- `generation_outbox` 已创建，状态持续为 `PENDING`。
- RabbitMQ 执行队列没有对应的新消息。
- 应用使用 Asia/Shanghai 时间创建记录，而 MySQL 容器的 `NOW()` 使用 UTC。

### 根因

Java 使用 `LocalDateTime.now()` 写入本地时间，部分 Mapper 又使用 MySQL `NOW()` 比较时间。同一条记录出现约 8 小时的比较偏差，导致 Outbox Relay 无法认领刚创建的事件。相同问题也会影响任务租约续期和过期恢复。

### 修复

- Outbox 认领、完成和重试统一传入应用侧 `LocalDateTime`。
- 任务认领、心跳、状态变更和租约恢复统一使用同一应用时间。
- Mapper 不再混用数据库 `NOW()` 和应用时间参数。

### 验证

- 新 Outbox 事件能够立即从 `PENDING` 变为 `SENT`。
- RabbitMQ Worker 能立即收到任务。
- 验证结束后，`generation_outbox` 中没有遗留的 `PENDING` 记录。

### 回归检查

- 提交后 1～2 秒内应出现 `TASK_QUEUED` 和 `TASK_RUNNING` 事件。
- 检查 `generation_outbox.last_error`、`locked_by`、`locked_until`。
- 宿主机、应用和数据库时区发生变化时，重新执行时间比较测试。

---

## GF-20260815-03：本地后端无法连接 MinIO

- 状态：已验证
- 影响范围：所有 Provider 的最终图片持久化
- 用户现象：Provider 已生成图片，但任务最终失败，错误为 `Image upload service failed`。

### 证据

- 底层异常为 `UnknownHostException: minio`。
- `.env` 的 `MINIO_ENDPOINT=http://minio:9000` 是 Docker 容器网络地址。
- 后端运行在宿主机时只能通过 `127.0.0.1:9100` 访问 MinIO。

### 根因

环境变量优先级高于 `application-local.yml`，Docker 专用的 `MINIO_ENDPOINT` 覆盖了本地 Profile 地址，宿主机无法解析 Docker Compose 服务名 `minio`。

### 修复

- MinIO 客户端增加独立的 `minio.client-endpoint`。
- 本地 Profile 使用 `MINIO_LOCAL_ENDPOINT`，默认 `http://127.0.0.1:9100`。
- Docker Profile 继续使用 `http://minio:9000`。
- 上传异常保留底层原因，便于从任务错误和日志中定位。

### 验证

- Mock 图片成功写入 MinIO `aigc` Bucket。
- `gen_task.image_url` 成功写入。
- 任务最终状态为 `SUCCESS`。

### 回归检查

- IDEA/宿主机启动时检查 `MINIO_LOCAL_ENDPOINT`。
- Compose 启动时检查 `MINIO_ENDPOINT`。
- 不要把 Docker 服务名当作浏览器或宿主机访问地址。

---

## GF-20260815-04：图片响应完成后被安全过滤器中断

- 状态：已验证
- 影响范围：任务缩略图、生成图库、图片下载
- 用户现象：图片数据已经存在，但浏览器报告图片读取失败或响应提前结束。

### 证据

- 图片接口实际返回了完整的 68 字节 Mock PNG。
- 客户端同时报告 `ResponseEnded`。
- 后端日志记录 `AccessDeniedException`，发生在 `StreamingResponseBody` 的异步分派阶段。

### 根因

初始图片请求已经携带 JWT 并完成任务归属校验，但 Servlet 容器完成流式响应时会发起 ASYNC 分派。该分派没有再次携带认证上下文，因此被 `.anyRequest().authenticated()` 拒绝。

### 修复

- Spring Security 放行 `DispatcherType.ASYNC` 和错误分派。
- 初始请求仍必须通过 JWT 和任务归属校验，未降低图片访问权限。

### 验证

- 图片接口返回 HTTP 200。
- 下载大小为 68 字节，SHA-256 校验一致。
- 后端不再出现 `AccessDeniedException`。
- 浏览器成功加载任务缩略图和图库图片。

### 回归检查

- 未携带 JWT 的初始图片请求仍应被拒绝。
- 用户不能读取其他用户的任务图片。
- 大图片下载时关注超时、内存和客户端中断日志。

---

## GF-20260815-05：前端缺少明确的图片管理入口

- 状态：已验证
- 影响范围：图片可发现性、历史结果管理
- 用户现象：不知道图片存在哪里，也找不到“素材库”或“图片历史”。

### 根因

原页面只在“最近任务”卡片中显示缩略图，没有单独的图片区域，也没有解释 MySQL 和 MinIO 的职责。

### 修复

- 新增“生成图库”区域，仅展示当前用户状态为 `SUCCESS` 的任务。
- 显示 Provider、尺寸、完成时间和下载入口。
- 页面明确标注“MySQL 元数据 · MinIO 原图”。
- 复用现有受保护图片接口，不让前端直连数据库或 MinIO 内网地址。

### 验证

- 浏览器中可见“生成图库”。
- 成功任务同时显示在最近任务和图库中。
- 图片使用 Blob URL 正常加载，浏览器控制台无警告或错误。

### 回归检查

- 失败、取消和处理中任务不应出现在图库。
- 切换用户后不得保留上一个用户的 Blob URL。
- 退出登录时应释放已创建的 Blob URL。

---

## GF-20260815-06：万相 API 协议与模型版本不匹配

- 状态：已验证
- 影响范围：阿里云百炼万相图片生成
- 用户现象：万相请求提交或结果解析失败。

### 根因

旧实现按早期文生图接口组织 `prompt`、`negative_prompt` 和 `results`，但当前默认模型为 `wan2.7-image-pro`。该模型使用 `input.messages[].content[]` 输入，并从 `output.choices[].message.content[].image` 读取结果。

### 修复

- 默认模型更新为 `wan2.7-image-pro`。
- 使用 `/services/aigc/image-generation/generation` 异步接口。
- 请求体更新为 `messages/content` 结构。
- 支持 `thinking_mode`、`watermark` 和新图片结果结构。
- 由于万相 2.7 没有独立反向提示词参数，将排除内容合并为提示词限制语句。
- 生成结果立即转存 MinIO，避免依赖有时效的第三方 URL。

### 验证

- `WanxImageProviderTest` 验证提交地址、请求头、请求体和结果解析。
- 本地数据库存在一条 `WANX / wan2.7-image-pro / SUCCESS` 记录，图片已写入 MinIO。
- 本次回归没有重复发起付费万相调用。

### 回归检查

- API Key 与 Endpoint 必须属于相同地域和业务空间。
- 模型升级时重新核对请求地址、输入结构、尺寸范围和返回字段。
- 记录万相业务错误码和请求 ID，但不能记录完整 API Key。
- 第三方图片 URL 必须在失效前转存到项目自己的对象存储。

---

## GF-20260818-01：本地后端启动时报 8080 已占用

- 状态：已验证
- 影响范围：IDEA/Maven 本地启动、Vite 开发代理、Docker Compose
- 用户现象：Spring Boot 启动失败，提示 `Port 8080 was already in use`。

### 证据

- 公共配置原来使用 `server.port=${SERVER_PORT:8080}`。
- `.env` 中存在 `SERVER_PORT=8080`，会以环境变量优先级覆盖 Profile 配置。
- 完整 Docker Compose 的 Nginx 也需要绑定宿主机 `8080`。
- 排查时 `8080` 和 `8081` 已无监听进程，说明发生冲突的旧进程后来已经退出；此前排障期间确实启动过监听 `8080` 的后台 Spring Boot 进程。

### 根因

本地 Spring Boot 与 Docker/Nginx 都默认使用宿主机 `8080`。此外，通用变量 `SERVER_PORT` 会被 Spring Boot 直接绑定为 `server.port`，即使在 `application-local.yml` 中指定其他默认值，只要加载了 `.env`，仍可能被强制覆盖回 `8080`。

### 修复

- 公共/Docker 端口变量改为 `APP_SERVER_PORT=8080`。
- `local` Profile 使用独立的 `LOCAL_SERVER_PORT=8081`。
- Vite 的 `/api`、`/user` 开发代理改到 `http://localhost:8081`。
- Docker 容器和 Nginx 仍使用内部及外部端口 `8080`。
- 同步更新 `.env`、`.env.template` 和 README。

### 验证

- 配置检查确认：`local → 8081`，Docker/Nginx → `8080`。
- Maven 测试通过：18 项通过、0 项失败；5 项依赖 Docker 的集成测试因当前沙箱无法访问 Docker API 而跳过。
- Vite 生产构建通过，开发代理目标为 `http://localhost:8081`。
- `git diff --check` 通过。

### 回归检查

- IDEA 启动必须激活 `local` Profile。
- 本地访问：前端 `5173`，后端直连 `8081`。
- Compose 访问：统一入口 `8080`。
- 如果宿主机 `8080` 已被其他软件占用，Docker 的 `8080:8080` 映射仍然无法启动；端口映射不能绕过宿主机端口占用。

---

## 新问题记录模板

复制以下内容到文档末尾，并同步更新“问题索引”。

```markdown
## GF-YYYYMMDD-NN：问题标题

- 状态：待确认
- 影响范围：
- 首次发现时间：
- 发现环境：local / docker / test / production
- 关联任务 ID、trace ID 或 request ID：
- 用户现象：

### 复现步骤

1.
2.
3.

### 期望结果


### 实际结果


### 证据

- HTTP 状态码：
- 前端错误：
- 后端异常：
- 数据库状态：
- RabbitMQ 状态：
- 存储状态：

### 根因


### 修复

- 修改文件：
- 配置变化：
- 数据迁移：无 / 有，说明：

### 验证

- 单元测试：
- 构建结果：
- 端到端测试：
- 浏览器验证：

### 回归检查

-

### 遗留事项

- 无
```

## 常用排查命令

以下命令只用于读取状态，不会修改业务数据。

```powershell
# 查看服务
docker compose ps

# 查看应用日志
docker compose logs --tail 200 app-1 app-2

# 查看 RabbitMQ 队列
docker exec aigc-gameflow-rabbitmq-1 rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers

# 查看最近任务
docker exec aigc-gameflow-mysql-1 mysql -uroot -p -D game_flow -e "SELECT task_uuid,status,provider,model,error_msg,image_url,create_time,update_time FROM gen_task ORDER BY id DESC LIMIT 20;"

# 查看最近生成事件
docker exec aigc-gameflow-mysql-1 mysql -uroot -p -D game_flow -e "SELECT task_uuid,event_type,message,create_time FROM generation_event ORDER BY id DESC LIMIT 50;"

# 后端测试
.\mvnw.cmd test

# 前端构建
Set-Location frontend
npm run build
```

## 更新记录

| 日期 | 内容 |
| --- | --- |
| 2026-08-15 | 创建统一故障档案，录入图片生成、Outbox、MinIO、安全响应、前端图库和万相 API 问题 |
| 2026-08-18 | 记录本地 8080 端口冲突，并拆分本地与 Docker 端口配置 |
