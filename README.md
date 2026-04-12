# AIGC GameFlow

一个面向游戏世界观构建与资产生成的 AI Native Java 后端项目。

这个项目适合作为大三到大四阶段的实习 Demo，重点不在“炫技式多 Agent”，而在于把 `用户鉴权 -> 异步任务 -> AI 提示词优化 -> 图像生成 -> 资产存储 -> 知识图谱查询` 这一整条后端链路串起来。

## 项目定位

- 面向岗位：`Java 后端`、`后端开发`、`AI 应用开发`、`Agent 应用开发`
- 业务场景：游戏角色设定、世界观知识沉淀、AIGC 资产生成
- 核心卖点：不是单纯的文生图页面，而是一个带 `Agent + 图谱 + 异步任务` 的后端系统原型

## 核心能力

- 用户注册、登录、JWT 鉴权
- 基于 LangChain4j 的 Agent 对话与工具调用
- 基于 RabbitMQ 的异步任务提交与消费
- 基于 Redis 的限流与任务缓存
- 基于 Neo4j 的角色知识图谱查询
- 基于 ComfyUI 的图像生成链路
- 基于 MinIO 的图片资产持久化
- 支持 SSE 流式聊天输出

## 技术栈

### 后端

- Java 21
- Spring Boot 3.3.4
- Spring Security
- MyBatis-Plus
- LangChain4j
- Hutool JWT

### 中间件与存储

- MySQL
- Redis
- RabbitMQ
- Neo4j
- MinIO

### AI 与生成侧

- DeepSeek API
- ComfyUI

### 前端

- Vue 3
- Element Plus
- Axios

## 系统架构

```text
Browser / Frontend
        |
        v
UserController / AgentController / TaskController
        |
        +--> Spring Security + JWT
        |
        +--> GameMasterAgent
        |         |
        |         +--> GameAgentTools
        |                    |
        |                    +--> KnowledgeService -> Neo4j
        |                    |
        |                    +--> TaskService
        |
        +--> TaskService -> MySQL
                     |
                     +--> Redis (限流 / 状态缓存)
                     |
                     +--> RabbitMQ
                               |
                               v
                         TaskListener
                               |
                               +--> AiPromptService
                               |
                               +--> ComfyUiService
                               |
                               +--> MinioService -> MinIO
```

## 核心流程

### 1. 自然语言对话生成图片

1. 用户向 `/agent/chat` 或 `/agent/chat/stream` 发送自然语言请求
2. `GameMasterAgent` 识别用户意图
3. 如果命中画图意图，触发 `drawImage` 工具
4. `TaskService` 创建任务、写库、限流、投递 RabbitMQ
5. `TaskListener` 消费任务并调用 AI 提示词优化与 ComfyUI
6. 生成结果上传 MinIO，并把任务状态更新回 MySQL / Redis
7. 前端轮询 `/task/{uuid}` 获取最终结果

### 2. 角色设定查询与沉淀

1. 用户在 Agent 对话中询问角色设定
2. Agent 调用 `queryLore`
3. `KnowledgeService` 从 Neo4j 查询角色节点和关系
4. 如果是新角色设定，Agent 可调用 `saveLore` 进行保存

## 模块说明

| 模块 | 说明 |
| --- | --- |
| `controller` | 对外接口层，包含用户、任务、Agent 相关接口 |
| `service` | 业务服务层，负责任务、AI、知识图谱、对象存储等逻辑 |
| `mq` | 异步任务监听与消费 |
| `mapper` | MyBatis-Plus 数据访问层 |
| `repository` | Neo4j 图谱访问层 |
| `model.entity` | MySQL 实体 |
| `model.graph` | Neo4j 图节点实体 |
| `resources/workflows` | ComfyUI 工作流模板 |
| `resources/static` | 演示前端页面 |

## 目录结构

```text
src/
  main/
    java/aigc/gameflow/
      config/
      controller/
      mapper/
      model/
      mq/
      repository/
      service/
      utils/
    resources/
      static/
      workflows/
      application.yml
      schema.sql
  test/
    java/
    resources/
```

## 运行前准备

本项目依赖较多，建议提前准备以下环境：

- JDK 21
- MySQL
- Redis
- RabbitMQ
- Neo4j
- MinIO
- ComfyUI
- DeepSeek API Key

## 配置说明

### 1. 数据库

执行 `src/main/resources/schema.sql` 初始化 MySQL 表结构。

### 2. 应用配置

主要配置位于 `src/main/resources/application.yml`：

- MySQL 连接
- Neo4j 连接
- RabbitMQ 连接
- Redis 连接
- MinIO 连接
- DeepSeek API
- ComfyUI 地址

### 3. API Key

推荐用环境变量注入：

```powershell
$env:DEEPSEEK_API_KEY="your-api-key"
.\mvnw.cmd spring-boot:run
```

也可以参考仓库内的 `QUICKSTART.txt` 与 `.env.template`。

## 启动步骤

### Windows

```powershell
.\mvnw.cmd spring-boot:run
```

### 使用启动脚本

```cmd
start.bat
```

## 主要接口

### 用户接口

- `POST /user/register`
- `POST /user/login`

### Agent 接口

- `POST /agent/chat`
- `POST /agent/chat/stream`

### 任务接口

- `POST /task/submit`
- `GET /task/{uuid}`

## Demo 展示建议

建议按下面顺序演示：

1. 登录系统
2. 在 Agent 界面输入自然语言需求
3. 展示流式回复
4. 捕获任务 UUID 并进入任务轮询
5. 展示生成结果图片
6. 补充说明任务是如何经过 RabbitMQ、Redis、MinIO、Neo4j 的

## 简历可写亮点

你可以从以下角度描述这个项目：

### 版本 A：偏 Java 后端

- 基于 Spring Boot 搭建 AIGC 异步任务平台，整合 JWT、Redis、RabbitMQ、MySQL、MinIO，实现任务提交、鉴权、缓存、异步消费与结果存储
- 设计图像生成任务链路，通过消息队列解耦请求入口与耗时生成流程，支持任务状态查询与结果持久化
- 使用 Neo4j 管理角色设定与关系图谱，为 Agent 场景提供知识查询能力

### 版本 B：偏 Agent / AI 应用

- 基于 LangChain4j 构建游戏世界观 Agent，支持自然语言对话、工具调用、角色知识查询与图片生成任务编排
- 接入 DeepSeek 与 ComfyUI，实现从自然语言需求到提示词优化、工作流生成、图片产出的完整 AI 应用链路
- 结合 Neo4j 图谱与任务系统，探索 Agent 在游戏设定生成和资产生产中的落地方式

### 版本 C：折中版

- 实现了一个面向游戏世界观构建的 AI Native 后端系统，整合 Agent 对话、知识图谱、异步任务和 AIGC 图片生成能力

## 当前适合继续打磨的方向

如果项目目标是“用于实习投递”，优先级建议如下：

1. 修复当前分支中 ComfyUI 调用接口未对齐的问题
2. 统一接口返回体与异常处理
3. 绑定任务与用户归属关系，补权限校验
4. 增加任务列表、失败原因、重试能力
5. 补测试、README、接口说明和演示材料

## 配套文档

- [项目链路图](/F:/coe/java/AIGC-GameFlow/docs/ARCHITECTURE_FLOW.md)
- [简历项目描述](/F:/coe/java/AIGC-GameFlow/docs/RESUME_PROJECT_DESC.md)
- [面试准备清单](/F:/coe/java/AIGC-GameFlow/docs/INTERVIEW_PREP.md)
- [广州投递策略](/F:/coe/java/AIGC-GameFlow/docs/GUANGZHOU_STRATEGY.md)

## 已知工程化改进点

当前仓库仍有一些适合继续完善的地方：

- `GameAssetService` 与 `ComfyUiService` 的调用接口需要对齐
- `TaskController` 返回结构与前端轮询逻辑需要统一
- 任务归属与用户权限控制还可以继续补强
- `KnowledgeService` 的保存链路需要进一步收口和校验
- 配置文件中的敏感信息建议完全迁移到环境变量

## 为什么这个项目适合当实习 Demo

因为它同时满足了三件事：

- 有真实后端工程要素，不只是页面拼装
- 有 AI 亮点，但没有脱离业务场景
- 讲得清楚业务闭环，适合中小厂技术面试展开追问

如果你后续想往 `Agent 开发` 走，这个项目也可以继续演进，但更建议以“会做 AI 落地的 Java 后端”作为当前求职定位。
