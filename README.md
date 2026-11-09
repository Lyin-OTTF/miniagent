# MiniAgent

不依赖任何 Agent 框架，从零实现一个最小可用的 Agent 运行时内核。

目的是把框架替你藏起来的那层执行模型写一遍：ReAct 循环怎么终止、工具 Schema 怎么在注册期校验、
模型吐出的参数为什么必须再校验一遍、以及进程崩溃后怎么续跑而不重放副作用。

## 技术栈

- Java 21，构建用 Maven
- 依赖仅 `Jackson`（JSON 序列化）+ `JUnit 5`（测试）
- 不引入 Spring、Spring AI、LangGraph4j 或任何 JSON Schema 校验库

## 能力

| 能力 | 实现位置 |
|---|---|
| ReAct 循环与三类终止条件（正常结束 / 轮次上限 / 重复调用） | `loop/AgentLoop`、`loop/TerminationPolicy` |
| 工具注册期校验（名称、重名、required 与 properties 一致性） | `tool/ToolRegistry` |
| JSON Schema 最小子集（`type` / `properties` / `required`） | `tool/JsonSchema` |
| Function Calling 参数解析与类型转换 | `tool/SchemaValidator` |
| 最终答案结构化校验（失败可重试，不打断会话） | `output/StructuredOutputValidator` |
| 快照、幂等键、副作用账本、HITL 审批、中断续跑 | `checkpoint/*`、`loop/AgentLoop#resume` |

## 设计要点

**续跑复用同一个循环。** `run` 与 `resume` 都进入 `loop(AgentState)`，区别只在起点：前者新建状态，
后者从快照恢复。续跑不是第二套逻辑，这是 checkpoint 执行模型的核心。

**快照必须是副本。** `Checkpoint.from` 对消息列表与账本都做拷贝，否则快照会跟着后续写入一起变化。

**副作用按幂等键去重。** 工具执行前先查账本，命中即回填历史结果、跳过执行，使已完成的写操作不重放。
语义边界是 at-least-once：若崩溃落在「工具已执行、账本未写入」之间，仍可能重放一次；
要 exactly-once 需配合幂等写或两阶段提交。

**模型与运行时解耦。** `LlmClient` 只有一个方法。测试用 `MockLlmClient` 按剧本精确出牌，
保证断言可复现；换成 `OpenAiCompatibleClient` 即可对接真实模型，循环代码一行不改。

## 目录结构

```
src/main/java/io/github/lyin/miniagent/
├── model/       # Role、Message、FunctionCall、ChatCompletionResponse
├── llm/         # LlmClient 抽象、OpenAI 兼容实现、Mock 替身
├── tool/        # Tool 接口、JsonSchema、注册表、SchemaValidator
├── loop/        # AgentLoop、AgentState、TerminationPolicy
├── output/      # 结构化输出校验
├── checkpoint/  # Checkpoint、快照存储、副作用账本、幂等键、HITL 闸门
├── Agent.java   # 门面
└── Main.java    # 演示入口（Mock，不联网）
```

## 构建与运行

```bash
mvn test      # 全部测试
```

`Main` 是演示入口，在 IDE 中直接运行即可：用 Mock 剧本跑一遍完整链路——
模型请求 `query_order` → 执行工具 → 模型给出最终答案，全程不联网、不消耗额度。
