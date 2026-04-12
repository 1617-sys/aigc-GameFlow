# 项目链路图

下面这张图是你当前项目的主链路，按“登录 -> 对话 -> 工具调用 -> 异步任务 -> 图片回传”串起来。

```mermaid
flowchart TD
    A[前端 index.html / agent.html] --> B[用户登录 /user/login]
    B --> C[UserController]
    C --> D[UserService]
    D --> E[(MySQL sys_user)]
    C --> F[ApiResponse<LoginResponse>]

    A --> G[输入自然语言 /agent/chat 或 /agent/chat/stream]
    G --> H[AgentController]
    H --> I[GameMasterAgent]
    I --> J[GameAgentTools]

    J --> K[queryLore]
    J --> L[saveLore]
    J --> M[drawImage]

    K --> N[KnowledgeService]
    L --> N
    N --> O[(Neo4j 角色图谱)]

    M --> P[TaskService]
    P --> Q[(Redis 限流 / 任务缓存)]
    P --> R[(MySQL gen_task)]
    P --> S[(RabbitMQ 队列)]

    S --> T[TaskListener]
    T --> U[GameAssetService]
    U --> V[AiPromptService / DeepSeek]
    U --> W[ComfyUiService]
    W --> X[ComfyUI]
    X --> Y[生成图片文件名]
    Y --> Z[MinioService]
    Z --> AA[(MinIO 图片存储)]
    T --> R
    T --> Q

    A --> AB[轮询 /task/{uuid}]
    AB --> AC[TaskController]
    AC --> P

    style A fill:#0d1117,stroke:#58a6ff,color:#fff
    style P fill:#161b22,stroke:#8b949e,color:#fff
    style T fill:#161b22,stroke:#8b949e,color:#fff
    style O fill:#0d1117,stroke:#2ea043,color:#fff
    style AA fill:#0d1117,stroke:#2ea043,color:#fff
```

## 你可以这样理解

- `UserController` 负责登录注册
- `AgentController` 负责接收自然语言
- `GameMasterAgent` 决定调用什么工具
- `GameAgentTools` 把“说人话”转成“业务动作”
- `TaskService` 负责创建异步任务
- `TaskListener` 负责真正执行生成流程
- `KnowledgeService` 负责图谱
- `ComfyUiService` 负责对接生成引擎
- `MinioService` 负责图片落盘

## 最终效果

用户的自然语言请求会变成一条完整闭环：

`前端输入` -> `Agent 识别意图` -> `工具调用` -> `异步任务` -> `AI 生成` -> `图片存储` -> `前端轮询拿结果`
