# AIGC-GameFlow Engine 升级实施说明

## 1. 升级后的项目定位

本次升级把旧项目从 `AIGC 游戏世界观 Demo` 调整为：

> AIGC-GameFlow Engine：面向 GameDev Agent Workbench 的多平台游戏资产生成执行引擎。

新项目负责 Agent 工具流、需求拆解、Prompt 生成和 Workflow 记录；旧项目负责接收生成任务、选择生图平台、异步执行、结果存储、状态回调和链路追踪。

## 2. 核心升级点

### 2.1 多平台生图 Provider 抽象

新增目录：

```text
src/main/java/aigc/gameflow/image
```

关键文件：

- `ImageGenerationProvider.java`
- `ComfyUiImageProvider.java`
- `OpenAiImageProvider.java`
- `ImageGenerationRouter.java`
- `ImageGenerationRequest.java`
- `ImageGenerationResult.java`
- `ProviderType.java`
- `ProviderPolicy.java`
- `GenerationStatus.java`

改造前：

```text
TaskListener -> GameAssetService -> ComfyUiService
```

所有生图逻辑都和 ComfyUI 强绑定。

改造后：

```text
TaskListener
-> ImageGenerationService
-> ImageGenerationRouter
-> ImageGenerationProvider
   -> ComfyUiImageProvider
   -> OpenAiImageProvider
```

这样后续新增 Replicate、Stability、通义万相、火山等平台时，只需要新增一个 Provider 实现，不需要重写任务主流程。

### 2.2 新增统一生成接口

新增文件：

```text
src/main/java/aigc/gameflow/controller/GenerationController.java
```

新增接口：

```text
POST /api/generation/jobs
GET  /api/generation/jobs/{taskUuid}
GET  /api/generation/jobs/{taskUuid}/events
GET  /api/generation/jobs
POST /api/generation/jobs/{taskUuid}/retry
POST /api/generation/jobs/{taskUuid}/cancel
GET  /api/generation/providers
```

旧接口 `/task/submit` 暂时保留，避免原有演示流程直接失效。新项目 `GameDev Agent Workbench` 后续应该优先调用 `/api/generation/jobs`。

### 2.3 任务字段升级

修改文件：

```text
src/main/java/aigc/gameflow/model/entity/GenTask.java
src/main/resources/schema.sql
```

新增字段：

```text
negative_prompt
provider
model
size
quality
provider_job_id
source_app
external_run_id
callback_url
callback_status
callback_error
latency_ms
trace_id
```

这些字段用于支持：

- 多平台模型选择
- 与新项目的 Workflow Run 绑定
- 生成耗时记录
- 回调状态记录
- trace 链路追踪

### 2.4 任务事件追踪

新增文件：

```text
src/main/java/aigc/gameflow/model/entity/GenerationEvent.java
src/main/java/aigc/gameflow/mapper/GenerationEventMapper.java
src/main/java/aigc/gameflow/service/GenerationEventService.java
src/main/java/aigc/gameflow/image/GenerationEventType.java
```

新增表：

```text
generation_event
```

现在任务执行过程中会记录事件：

```text
TASK_CREATED
TASK_QUEUED
TASK_RUNNING
TASK_CANCELED
TASK_RETRY_REQUESTED
PROVIDER_SELECTED
PROVIDER_REQUEST_SENT
IMAGE_STORED
CALLBACK_SENT
CALLBACK_FAILED
TASK_SUCCESS
TASK_FAILED
```

这相当于轻量版 Langfuse trace，方便排查“任务为什么失败、卡在哪一步、用了哪个平台、耗时多少”。

### 2.5 MQ 消费逻辑升级

修改文件：

```text
src/main/java/aigc/gameflow/mq/TaskListener.java
```

改造前：

- 消费 MQ 后直接调用 ComfyUI。
- 轮询、下载、上传、状态更新都混在 Listener 里。

改造后：

- Listener 只负责消费任务、更新状态、调用统一生成服务。
- 具体平台选择交给 `ImageGenerationRouter`。
- 结果存储交给 `ImageGenerationService`。
- 成功或失败都会写入 `generation_event`。
- 支持取消任务时跳过执行或忽略结果。

### 2.6 回调机制升级

修改文件：

```text
src/main/java/aigc/gameflow/service/CallbackService.java
```

现在如果任务提交时传入 `callbackUrl`，任务完成或失败后会主动回调上游项目。

回调内容包括：

```text
taskUuid
externalRunId
status
imageUrl
provider
model
latencyMs
errorMsg
traceId
```

同时会写入：

```text
callback_status
callback_error
```

### 2.7 Docker 部署升级

新增文件：

```text
Dockerfile
docker-compose.yml
src/main/resources/application-docker.yml
```

修改文件：

```text
.env.template
src/main/resources/application.yml
```

现在项目支持通过环境变量配置：

```text
MySQL
Redis
RabbitMQ
MinIO
Neo4j
DeepSeek
OpenAI Image API
ComfyUI
```

Docker Compose 默认包含：

```text
app
mysql
redis
rabbitmq
minio
neo4j
```

ComfyUI 不强行塞进 compose，因为 GPU 环境差异很大，推荐作为外部服务配置 `COMFYUI_BASE_URL`。

### 2.8 MinIO 初始化升级

修改文件：

```text
src/main/java/aigc/gameflow/config/MinioConfig.java
```

上传图片前会懒加载检查并创建配置的 bucket。这样比启动时强制连接 MinIO 更稳，避免 Docker Compose 中 MinIO 还没 ready 时导致 Spring Boot 应用启动失败。

## 3. 新项目如何调用旧项目

新项目生成 Prompt 后调用：

```text
POST /api/generation/jobs
```

请求示例：

```json
{
  "prompt": "dark fantasy NPC, concept art",
  "negativePrompt": "low quality, blurry",
  "preferredProvider": "OPENAI",
  "providerPolicy": "QUALITY_FIRST",
  "model": "gpt-image-1",
  "size": "1024x1024",
  "quality": "auto",
  "sourceApp": "gamedev-agent-workbench",
  "externalRunId": "agent-run-uuid",
  "callbackUrl": "http://localhost:5173/api/integrations/gameflow/callback"
}
```

返回：

```json
{
  "code": 200,
  "msg": "generation job submitted",
  "data": {
    "taskUuid": "xxx",
    "status": "PENDING",
    "provider": "OPENAI",
    "traceId": "xxx"
  }
}
```

然后新项目可以：

- 用 `GET /api/generation/jobs/{taskUuid}` 查任务。
- 用 `GET /api/generation/jobs/{taskUuid}/events` 查 trace。
- 等待旧项目通过 `callbackUrl` 主动回调。

## 4. 为什么这样升级

这次升级的核心不是“堆更多 AI 名词”，而是让旧项目更像真实 AI 工程系统：

- 统一任务入口，便于被其他项目调用。
- 多 Provider 抽象，便于接入不同模型平台。
- MQ 异步执行，避免生图阻塞接口。
- MinIO 统一存储，屏蔽不同平台返回格式差异。
- 事件追踪，方便调试失败任务。
- Docker 编排，方便部署和演示。

## 5. 后续建议

下一轮可以继续做：

1. 把旧静态页面归档或删除，让旧项目彻底变成 API Engine。
2. 把 `ComfyUiService` 拆成 `ComfyUiClient`，进一步降低职责复杂度。
3. 新增 `ProviderConfig` 表，在后台控制不同平台是否启用。
4. 对 `generation_event` 做分页查询和按 `trace_id` 查询。
5. 给 `/api/generation/jobs` 写 Apifox / Bruno 接口集合。
6. 增加 Docker 健康检查和 MinIO bucket 初始化脚本。
