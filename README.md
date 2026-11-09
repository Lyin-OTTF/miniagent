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

## 已知限制

这些是有意保留的边界，不是未完成项：

- **只取首个 tool call。** 模型一次返回多个工具调用时，仅执行第一个，其余丢弃。
  多工具并行需要把单次循环体改成扇出/汇聚结构。
- **副作用去重是 at-least-once。** 若崩溃恰好落在「工具已执行、账本未写入」之间，续跑会重放一次。
  exactly-once 需工具侧幂等写或两阶段提交。
- **重试提示复用 user 角色。** 结构化输出校验失败时，纠正提示以 `user` 消息回填，
  会在历史里多出一条「用户发言」。改成 `system` 语义更准，但部分契约要求 system 只在开头。
- **重试计数不进快照。** 结构化输出的重试次数与循环检测的重复计数都是 loop 实例字段，
  续跑到新实例后归零。单实例续跑不受影响。
- **`tools` 为空时仍发送空数组。** 部分兼容实现可能不接受，真实模型调用建议至少注册一个工具。

## 目录结构

```
src/main/java/io/github/lyin/miniagent/
├── model/       # Role、Message、FunctionCall、ChatCompletionResponse
├── llm/         # LlmClient 抽象、OpenAI 兼容实现、Mock 替身
├── tool/        # Tool 接口、JsonSchema、注册表、SchemaValidator
├── loop/        # AgentLoop、AgentState、TerminationPolicy
├── output/      # 结构化输出校验
├── checkpoint/  # Checkpoint、快照存储、副作用账本、幂等键、HITL 闸门
├── Agent.java       # 门面
├── Main.java        # 演示入口（Mock 剧本，不联网）
└── RealApiDemo.java # 真实模型联调入口（需 Key，联网）
```

## 构建与运行

```bash
mvn test      # 全部测试
```

`Main` 是演示入口，用 Mock 剧本跑两段流程，全程不联网、不消耗额度：

1. **工具调用闭环**：模型请求 `query_order` → 执行 → 模型给出答案。
2. **中断与续跑**：第 3 次模型调用时模拟崩溃，`deduct_stock` 已执行并记账；
   从快照续跑后模型**再次请求** `deduct_stock`，账本判定幂等键已执行、按历史结果回填，
   因此该写操作全程只执行一次。输出会打印三段计数（query / deduct / notify）供对照。

```text
[1] 首次执行中断: 模拟第 3 次模型调用时进程崩溃
    中断时累计执行次数: query_order=1, deduct_stock=1, notify=0
[2] 从快照续跑完成
    终止原因: finished
    续跑后累计执行次数: query_order=1, deduct_stock=1, notify=1
```

## 对接真实模型

`RealApiDemo` 把 `LlmClient` 换成真实实现跑一轮工具调用。Mock 只能证明循环与账本逻辑正确，
证明不了「上游会接受我们发出的请求体」——协议字段是否合规只能由真实服务裁决。

```bash
export DEEPSEEK_API_KEY=sk-xxxx          # 必需，不接受命令行参数，避免落进 shell 历史
export DEEPSEEK_BASE_URL=...             # 可选，默认 https://api.deepseek.com
export DEEPSEEK_MODEL=...                # 可选，默认 deepseek-chat

mvn -q compile dependency:build-classpath -Dmdep.outputFile=cp.txt
java -Dstdout.encoding=UTF-8 -cp "target/classes:$(cat cp.txt)" io.github.lyin.miniagent.RealApiDemo
```

Windows 下类路径分隔符是 `;` 而非 `:`，也可以直接在 IDE 里运行该 main 方法。
`-Dstdout.encoding=UTF-8` 是为了让 Windows 控制台不把中文打成乱码。

实测输出（DeepSeek `deepseek-chat`）：

```text
终止原因: finished
工具实际执行次数: query_order=1
消息历史:
  [user] 请查询订单 A1001 的状态。必须先调用 query_order 工具，再根据工具结果回答。
  [assistant] [请求工具 query_order 参数={"orderId": "A1001"} id=call_00_PgYCVsKdT4U40PAeL9TN9494]
  [tool] 订单 A1001 状态：已发货，承运商：顺丰，运单号 SF1234567890
  [assistant] 订单 A1001 的查询结果如下：- **状态**：已发货 ...
```

这一轮的第二个请求必然携带 `assistant.tool_calls`（含上游返回的 `id`）与 `tool.tool_call_id`，
上游正常返回即说明多轮工具调用的协议字段是被真实服务校验通过的，不只是本地桩认为合规。

