# 简历项目描述

下面这份内容是给你直接放进简历的版本，我按 `Java 后端主投` 和 `Agent/AI 应用亮点` 两种角度都整理了。

## 项目名称建议

- `AIGC GameFlow`
- `AIGC 游戏资产异步任务平台`
- `基于 LangChain4j 的游戏世界观 Agent 系统`

## 推荐简历标题

`AIGC GameFlow：面向游戏世界观构建与资产生成的 AI Native 后端系统`

## 版本一：主投 Java 后端

- 基于 `Spring Boot` 搭建 AIGC 异步任务平台，集成 `JWT` 鉴权、`Redis` 限流、`RabbitMQ` 异步队列、`MySQL` 持久化与 `MinIO` 资产存储，实现从任务提交到结果落库的完整闭环。
- 设计图片生成任务链路，将自然语言请求与耗时生成流程解耦，支持任务状态查询、失败重试和历史任务追踪。
- 通过 `Neo4j` 维护游戏角色设定与关系图谱，为 Agent 查询角色背景、自动补全设定提供知识支撑。

## 版本二：主投 Agent / AI 应用

- 基于 `LangChain4j` 构建游戏世界观 Agent，支持自然语言对话、工具调用、知识库查询与图片生成任务编排。
- 接入 `DeepSeek` 与 `ComfyUI`，实现从提示词优化、工作流生成到图片输出的 AI 应用闭环。
- 结合 `Neo4j` 图谱和 `MQ` 异步任务系统，探索 Agent 在游戏角色设定、世界观构建和资产生产场景中的落地方式。

## 版本三：折中写法

- 实现了一个面向游戏世界观构建的 AI Native 后端系统，整合 Agent 对话、知识图谱、异步任务和 AIGC 图片生成能力。

## 你可以在简历里写的技术关键词

- `Spring Boot`
- `Spring Security`
- `JWT`
- `MyBatis-Plus`
- `Redis`
- `RabbitMQ`
- `Neo4j`
- `MinIO`
- `LangChain4j`
- `DeepSeek`
- `ComfyUI`
- `SSE`

## 面试时的项目亮点说法

你可以重点讲这 4 个点：

1. 为什么要用 `RabbitMQ` 解耦生图任务
2. `Redis` 在项目里做了限流和任务缓存
3. `Neo4j` 为什么适合做角色关系和设定图谱
4. `Agent` 怎么调用工具把自然语言变成业务动作

## 不建议的写法

- 不要写“自研 Agent 框架”
- 不要写“高可用分布式平台”
- 不要写“完全媲美工业级生产系统”

这些容易被面试官追问穿。

## 建议你在简历里的呈现方式

如果简历只有一段项目描述，建议写成：

> 基于 Spring Boot + LangChain4j + ComfyUI 实现 AIGC 游戏资产异步任务平台，支持 JWT 登录鉴权、Redis 限流、RabbitMQ 异步任务、Neo4j 角色图谱查询和 MinIO 结果存储，完成从自然语言输入到图片生成、任务追踪、知识沉淀的完整业务闭环。

