package io.github.lyin.miniagent;

import io.github.lyin.miniagent.checkpoint.CheckpointStore;
import io.github.lyin.miniagent.checkpoint.HitlGate;
import io.github.lyin.miniagent.checkpoint.SideEffectLedger;
import io.github.lyin.miniagent.llm.LlmClient;
import io.github.lyin.miniagent.loop.AgentLoop;
import io.github.lyin.miniagent.loop.AgentState;
import io.github.lyin.miniagent.loop.TerminationPolicy;
import io.github.lyin.miniagent.tool.JsonSchema;
import io.github.lyin.miniagent.tool.SchemaValidator;
import io.github.lyin.miniagent.tool.Tool;
import io.github.lyin.miniagent.tool.ToolRegistry;

/**
 * 对外门面：把 {@link AgentLoop} 与协作组件装配成可直接使用的对象。
 *
 * <p>门面存在的理由是「会话状态必须跨多次调用存活」：{@link AgentLoop} 只负责单次执行，
 * 而一次会话要共享同一个 {@link CheckpointStore} 与 {@link SideEffectLedger}，
 * 否则中断后无法按 sessionId 找回快照。故这些组件由门面持有，每次执行时再组装 loop。</p>
 */
public class Agent {

    private final ToolRegistry registry = new ToolRegistry();
    private final CheckpointStore checkpointStore = new CheckpointStore();
    private final LlmClient llmClient;
    private final String sessionId;
    private final TerminationPolicy terminationPolicy;
    private final SchemaValidator schemaValidator;
    private final SideEffectLedger ledger;
    private final HitlGate hitlGate;
    private JsonSchema expectedSchema = null;
    private int outputMaxRetries = 0;

    /**
     * 默认装配：轮次上限 10、连续重复 5 次判定死循环、HITL 全放行。
     *
     * @param llmClient 模型客户端，Mock 或真实 OpenAI 兼容实现均可
     * @param sessionId 会话标识，中断续跑时用它找回快照
     */
    public Agent(LlmClient llmClient, String sessionId) {
        this(llmClient, sessionId, new HitlGate());
    }

    /**
     * 可注入审批策略的装配方式，用于需要人工介入（HITL）的场景。
     */
    public Agent(LlmClient llmClient, String sessionId, HitlGate hitlGate) {
        this.llmClient = llmClient;
        this.sessionId = sessionId;
        this.terminationPolicy = new TerminationPolicy(10, 5);
        this.schemaValidator = new SchemaValidator();
        this.ledger = new SideEffectLedger();
        this.hitlGate = hitlGate;
    }

    public void registerTool(Tool tool) {
        registry.register(tool);
    }

    /**
     * 配置最终答案的结构化输出校验。
     *
     * @param expectedSchema 期望的 JSON 结构
     * @param maxRetries      校验失败后最多重试次数
     */
    public void setExpectedOutput(JsonSchema expectedSchema, int maxRetries) {
        this.expectedSchema = expectedSchema;
        this.outputMaxRetries = maxRetries;
    }

    /**
     * 从头跑一次对话。
     */
    public AgentState run(String userInput) {
        return newLoop().run(userInput);
    }

    /**
     * 从上次持久化的快照继续跑。
     *
     * <p>每次执行都新建 loop，是为了让循环体内的临时状态不跨会话残留；
     * 会话级状态（消息历史、副作用账本）由快照恢复，不依赖 loop 实例存活。</p>
     */
    public AgentState resume() {
        return newLoop().resume(sessionId);
    }

    private AgentLoop newLoop() {
        return new AgentLoop(llmClient, registry, terminationPolicy, schemaValidator,
                ledger, hitlGate, checkpointStore, sessionId, expectedSchema, outputMaxRetries);
    }
}
