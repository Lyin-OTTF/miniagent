# MiniAgent

手写 Agent Runtime 内核（原理实践）。

在**不使用任何第三方 Agent 框架**（无 Spring AI / LangGraph4j）的前提下，从零实现一个最小可用的 Agent 运行时，用以理解框架替你藏起来的执行模型。

## 技术栈

- Java 21
- 依赖仅 `Jackson`（JSON）+ `JUnit 5`（测试），无 Spring、无 Agent 框架

## 能力清单（对应简历 claim）

1. Agent Loop 终止条件设计
2. 工具 Schema 注册与校验
3. Function Calling 参数解析
4. 结构化输出校验
5. Checkpoint 快照 + 幂等键 + 副作用记录 + HITL 中断续跑，验证失败续跑不重放副作用调用

## 目录结构

```
src/main/java/io/github/lyin/miniagent/
├── io.github.lyin.miniagent.model/        # 消息、角色、工具调用、响应等数据模型
├── llm/          # LLM 客户端抽象：Mock 替身 + OpenAI 兼容真实实现
├── tool/         # 工具接口、JSON Schema、工具注册表
├── schema/       # JSON Schema 最小子集校验器（参数解析 / 结构化输出校验共用）
├── loop/         # Agent Loop 与终止条件
├── output/       # 结构化输出校验
├── checkpoint/   # Checkpoint 快照、副作用账本、幂等键、HITL 闸门
└── Agent.java    # 门面，串起整个运行时
```

## 构建与测试

```bash
mvn compile   # 编译
mvn test      # 运行测试
```
