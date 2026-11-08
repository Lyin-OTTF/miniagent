package io.github.lyin.miniagent;

import io.github.lyin.miniagent.checkpoint.*;
import io.github.lyin.miniagent.llm.LlmClient;
import io.github.lyin.miniagent.loop.AgentLoop;
import io.github.lyin.miniagent.loop.AgentState;
import io.github.lyin.miniagent.loop.TerminationPolicy;
import io.github.lyin.miniagent.tool.SchemaValidator;
import io.github.lyin.miniagent.tool.Tool;
import io.github.lyin.miniagent.tool.ToolRegistry;

public class Agent {

    // 🔴 成员变量：把所有组件都包进来
    // 1. ToolRegistry registry
    // 2. LlmClient llmClient
    // 3. CheckpointStore checkpointStore
    // 4. String sessionId
    private ToolRegistry registry;
    private LlmClient llmClient;
    private CheckpointStore checkpointStore;
    private String sessionId;
    private final TerminationPolicy terminationPolicy;
    private final SchemaValidator schemaValidator;
    private final SideEffectLedger ledger;
    private final HitlGate hitlGate;

    // 🔴 构造器：接收 LlmClient（用户自己选 Mock 还是 DeepSeek）
    public Agent(LlmClient llmClient, String sessionId) {
         this.registry = new ToolRegistry();
         this.llmClient = llmClient;
         this.checkpointStore = new CheckpointStore();
         this.sessionId = sessionId;
         this.terminationPolicy = new TerminationPolicy(10, 5);
         this.schemaValidator = new SchemaValidator();
         this.ledger = new SideEffectLedger();
         this.hitlGate = new HitlGate();
    }

    // 🔴 注册工具：对外暴露
     public void registerTool(Tool tool) { registry.register(tool); }

    // 🔴 run 方法：跑一次对话
     public AgentState run(String userInput) {
         // new 一个 AgentLoop，把所有组件传进去
         AgentLoop loop = new AgentLoop(llmClient, registry,terminationPolicy,schemaValidator,ledger,hitlGate,checkpointStore, sessionId);
        // 调 loop.run(userInput)
         AgentState state = loop.run(userInput);
         // 返回 state
         return state;
}}