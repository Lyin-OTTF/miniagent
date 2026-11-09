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

import java.util.List;
import java.util.Map;

public class AgentLoop {

    private final LlmClient llmClient;
    private final ToolRegistry registry;
    private final TerminationPolicy terminationPolicy;
    private final SchemaValidator schemaValidator;
    private final HitlGate hitlGate;
    private final CheckpointStore checkpointStore;
    private final String sessionId;

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

    public AgentState run(String userInput) {
        AgentState state = new AgentState();
        state.appendMessage(new Message(Role.USER, userInput, null));
        return loop(state);
    }

    public AgentState resume(String sessionId) {
        if (!this.sessionId.equals(sessionId)) {
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

    private AgentState loop(AgentState state) {
        List<Tool> availableTools = registry.listTools();
        while (true) {
            ChatCompletionResponse response = llmClient.chat(state.getMessages(), availableTools);

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
                    result = ToolResult.failure("用户拒绝执行: " + tool.name());
                }
            }

            String toolCallId = functionCall.getId();
            state.appendMessage(new Message(Role.TOOL, result.getText(), null, toolCallId));

            state.nextTurn();

            checkpointStore.save(sessionId, Checkpoint.from(state, ledger));
        }
        return state;
    }
}
