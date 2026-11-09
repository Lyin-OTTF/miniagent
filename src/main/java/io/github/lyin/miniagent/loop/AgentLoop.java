package io.github.lyin.miniagent.loop;

import io.github.lyin.miniagent.checkpoint.Checkpoint;
import io.github.lyin.miniagent.checkpoint.CheckpointStore;
import io.github.lyin.miniagent.checkpoint.HitlGate;
import io.github.lyin.miniagent.checkpoint.IdempotencyKey;
import io.github.lyin.miniagent.checkpoint.SideEffectLedger;
import io.github.lyin.miniagent.llm.LlmClient;
import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.FunctionCall;
import io.github.lyin.miniagent.model.Message;
import io.github.lyin.miniagent.model.Role;
import io.github.lyin.miniagent.tool.SchemaValidator;
import io.github.lyin.miniagent.tool.Tool;
import io.github.lyin.miniagent.tool.ToolRegistry;
import io.github.lyin.miniagent.tool.ToolResult;

import java.util.Map;

/**
 * ReAct 循环：问模型 → 判断是否终止 → 执行工具 → 存快照，直到终止条件命中。
 *
 * <p>「从头跑」与「续跑」共用同一个 {@link #loop(AgentState)}：续跑不是另一套逻辑，
 * 只是以快照恢复出的状态作为起点进入同一个循环。这正是 checkpoint 执行模型的本质。</p>
 *
 * <p>副作用安全依赖 {@link SideEffectLedger}：工具执行前先按幂等键查账本，
 * 命中即回填历史结果、跳过执行，使续跑不会重复扣款或重复扣库存。
 * 语义边界见 {@link SideEffectLedger} 的类注释（at-least-once）。</p>
 */
public class AgentLoop {

    private final LlmClient llmClient;
    private final ToolRegistry registry;
    private final TerminationPolicy terminationPolicy;
    private final SchemaValidator schemaValidator;
    private final HitlGate hitlGate;
    private final CheckpointStore checkpointStore;
    private final String sessionId;

    /** 续跑时会被快照里的账本整体替换，故不能是 final。 */
    private SideEffectLedger ledger;

    public AgentLoop(LlmClient llmClient, ToolRegistry registry, TerminationPolicy terminationPolicy,
                     SchemaValidator schemaValidator, SideEffectLedger ledger, HitlGate hitlGate,
                     CheckpointStore checkpointStore, String sessionId) {
        this.llmClient = llmClient;
        this.registry = registry;
        this.terminationPolicy = terminationPolicy;
        this.schemaValidator = schemaValidator;
        this.ledger = ledger;
        this.hitlGate = hitlGate;
        this.checkpointStore = checkpointStore;
        this.sessionId = sessionId;
    }

    /**
     * 从头开始一次对话。
     */
    public AgentState run(String userInput) {
        AgentState state = new AgentState();
        state.appendMessage(new Message(Role.USER, userInput, null));
        return loop(state);
    }

    /**
     * 从快照恢复并继续执行。
     *
     * <p>不重复 append 用户消息：用户输入已在崩溃前的快照里，
     * 再追加一次会让模型看到两遍同一个请求。</p>
     *
     * @throws IllegalStateException    该会话没有快照
     * @throws IllegalArgumentException 传入的会话与当前 loop 绑定的会话不一致
     */
    public AgentState resume(String sessionId) {
        if (!this.sessionId.equals(sessionId)) {
            // 快照按入参加载、却按 this.sessionId 保存，不一致会把恢复后的进度写进另一个会话
            throw new IllegalArgumentException(
                    "会话不匹配: loop=" + this.sessionId + ", resume=" + sessionId);
        }
        Checkpoint checkpoint = checkpointStore.load(sessionId);
        if (checkpoint == null) {
            throw new IllegalStateException("无快照: " + sessionId);
        }
        AgentState state = AgentState.restore(checkpoint.getMessages(), checkpoint.getTurn());
        this.ledger = checkpoint.getLedger();
        return loop(state);
    }

    /**
     * 循环体，run 与 resume 共用。
     */
    private AgentState loop(AgentState state) {
        while (true) {
            ChatCompletionResponse response = llmClient.chat(state.getMessages());

            Message assistantMsg = response.hasToolCall()
                    ? new Message(Role.ASSISTANT, null, response.getToolCall())
                    : new Message(Role.ASSISTANT, response.getContent(), null);
            state.appendMessage(assistantMsg);

            String stopReason = terminationPolicy.check(state, response);
            if (stopReason != null) {
                state.stop(stopReason);
                break;
            }

            FunctionCall functionCall = response.getToolCall();
            Tool tool = registry.get(functionCall.getName());
            Map<String, Object> args = schemaValidator.validate(functionCall.getArguments(), tool.schema());

            String idempotencyKey = IdempotencyKey.of(tool.name(), args);
            ToolResult result = ledger.lookup(idempotencyKey);
            if (result == null) {
                if (hitlGate.approve(tool, args)) {
                    result = tool.call(args);
                    ledger.record(idempotencyKey, result);
                } else {
                    // 被拒绝不记账：人工拒绝不是「已完成」，下次仍应重新征求审批
                    result = ToolResult.failure("用户拒绝执行: " + tool.name());
                }
            }
            state.appendMessage(new Message(Role.TOOL, result.getText(), null));

            state.nextTurn();

            checkpointStore.save(sessionId, Checkpoint.from(state, ledger));
        }
        return state;
    }
}
