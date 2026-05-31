# AIGC-GameFlow Engine 升级方案

## 1. 新定位

旧项目从 `AI 游戏世界观 Demo` 升级为：

> AIGC-GameFlow Engine：面向 GameDev Agent Workbench 的多平台游戏资产生成执行引擎。

新项目 `GameDev Agent Workbench` 负责需求拆解、Prompt 生成、Workflow 记录和 Agent 工具流；旧项目负责接收生成任务、选择生图平台、异步执行、存储结果、回调状态和记录生成链路。

## 2. 核心目标

1. 支持多平台生图，不再只绑定 ComfyUI。
2. 保留 ComfyUI 本地工作流能力，同时新增 OpenAI GPT Image API 生成能力。
3. 暴露适合新项目调用的统一生成接口。
4. 把任务状态从数字语义升级为清晰枚举语义。
5. 统一结果存储，所有平台结果最终都转存到 MinIO。
6. 补齐 Docker 部署能力，降低本地演示和部署成本。
7. 逐步清理静态 demo、乱码注释、过重 Service、散乱面试文档。

## 3. 参考市面 AI 软件的局部设计

### Dify

参考它的 workflow 思路：上游产生 Prompt 和执行步骤，下游执行具体能力。旧项目只做生成执行层，不复刻 Dify。

### Langfuse

参考它的 trace 思路：每次 AI 调用要记录输入、输出、耗时、错误和调用平台。旧项目后续可新增 `generation_event` 表做轻量 trace。

### ComfyUI

继续作为本地高可控图像工作流平台，适合 Flux、SDXL、LoRA、ControlNet 等场景。

### OpenAI GPT Image API

作为云端快速生图平台，适合没有 GPU 的部署环境，也适合面试演示。

## 4. 第一阶段 MVP

本阶段先做主干升级：

1. 新增统一生成接口 `/api/generation/jobs`。
2. 新增 Provider 抽象：
   - `COMFYUI`
   - `OPENAI`
3. 新增路由策略：
   - `AUTO`
   - `LOCAL_FIRST`
   - `QUALITY_FIRST`
   - `COST_FIRST`
4. 改造 MQ 消费逻辑，让消费者只调用统一生成服务。
5. 新增新项目联动字段：
   - `sourceApp`
   - `externalRunId`
   - `callbackUrl`
6. 保留旧 `/task/submit`，避免历史功能直接断掉。
7. 补 Dockerfile、docker-compose、`application-docker.yml`。

## 5. 第二阶段增强

1. 新增 `generation_event` 表记录任务事件。
2. 新增回调重试和回调日志。
3. 新增 Provider 配置表，支持后台开关平台。
4. 新增任务取消、重试、批量查询。
5. 新增缩略图生成和图片元信息。
6. 归档旧静态页和冗余面试文档。

## 6. 推荐接口

### 提交生成任务

`POST /api/generation/jobs`

```json
{
  "prompt": "dark fantasy NPC, concept art",
  "negativePrompt": "low quality, blurry",
  "preferredProvider": "OPENAI",
  "providerPolicy": "QUALITY_FIRST",
  "model": "gpt-image-2",
  "size": "1024x1024",
  "quality": "medium",
  "sourceApp": "gamedev-agent-workbench",
  "externalRunId": "agent-run-uuid",
  "callbackUrl": "http://localhost:5173/api/integrations/gameflow/callback"
}
```

### 查询任务

`GET /api/generation/jobs/{taskUuid}`

### 查询平台能力

`GET /api/generation/providers`

## 7. 任务状态

旧状态兼容：

- `0` -> `PENDING`
- `1` -> `RUNNING`
- `2` -> `SUCCESS`
- `3` -> `FAILED`

新代码中优先使用枚举：

- `PENDING`
- `RUNNING`
- `SUCCESS`
- `FAILED`
- `CANCELED`
- `RETRYING`

## 8. 数据库升级方向

第一阶段在原 `gen_task` 表上追加字段，降低迁移成本：

- `provider`
- `model`
- `negative_prompt`
- `size`
- `quality`
- `provider_job_id`
- `source_app`
- `external_run_id`
- `callback_url`
- `latency_ms`
- `trace_id`

第二阶段可以重命名为 `generation_task`。

## 9. Docker 部署设计

基础服务：

- Spring Boot App
- MySQL 8
- Redis
- RabbitMQ Management
- MinIO
- Neo4j，可选

不强制把 ComfyUI 放进 compose，因为 GPU 环境差异很大。部署时支持两种模式：

- 无 GPU：使用 OpenAI Provider。
- 有 GPU：配置外部 ComfyUI URL。

## 10. 推荐开发顺序

1. 新增升级文档。
2. 新增 DTO 和枚举。
3. 新增 Provider 抽象和 OpenAI Provider。
4. 把 ComfyUI 封装为 Provider。
5. 改造任务服务和 MQ 消费。
6. 新增 `/api/generation` 接口。
7. 补 Docker 部署文件。
8. 编译验证。
9. 第二阶段再清理静态页和文档归档。

