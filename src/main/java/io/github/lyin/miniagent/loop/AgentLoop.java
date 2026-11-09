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
import io.github.lyin.miniagent.output.StructuredOutputValidator;
import io.github.lyin.miniagent.tool.JsonSchema;
import io.github.lyin.miniagent.tool.SchemaValidator;
import io.github.lyin.miniagent.tool.Tool;
import io.github.lyin.miniagent.tool.ToolRegistry;
import io.github.lyin.miniagent.tool.ToolResult;

import java.util.List;
import java.util.Map;

public class AgentLoop {

    public static final String REASON_OUTPUT_INVALID = "output_invalid";

    private final LlmClient llmClient;
    private final ToolRegistry registry;
    private final TerminationPolicy terminationPolicy;
    private final SchemaValidator schemaValidator;
    private final HitlGate hitlGate;
    private final CheckpointStore checkpointStore;
    private final String sessionId;

    private final JsonSchema expectedSchema;
    private final int maxRetries;
    private final StructuredOutputValidator outputValidator = new StructuredOutputValidator();

    private SideEffectLedger ledger;
    private int outputRetryCount = 0;

    public AgentLoop(LlmClient llmClient, ToolRegistry registry, TerminationPolicy terminationPolicy,
                     SchemaValidator schemaValidator, SideEffectLedger ledger, HitlGate hitlGate,
                     CheckpointStore checkpointStore, String sessionId) {
        this(llmClient, registry, terminationPolicy, schemaValidator, ledger, hitlGate,
                checkpointStore, sessionId, null, 0);
    }

    public AgentLoop(LlmClient llmClient, ToolRegistry registry, TerminationPolicy terminationPolicy,
                     SchemaValidator schemaValidator, SideEffectLedger ledger, HitlGate hitlGate,
                     CheckpointStore checkpointStore, String sessionId,
                     JsonSchema expectedSchema, int maxRetries) {
        this.llmClient = llmClient;
        this.registry = registry;
        this.terminationPolicy = terminationPolicy;
        this.schemaValidator = schemaValidator;
        this.ledger = ledger;
        this.hitlGate = hitlGate;
        this.checkpointStore = checkpointStore;
        this.sessionId = sessionId;
        this.expectedSchema = expectedSchema;
        this.maxRetries = maxRetries;
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

            if (stopReason != null && stopReason.equals(TerminationPolicy.REASON_FINISHED)) {
                if (expectedSchema != null) {
                    StructuredOutputValidator.OutputResult result =
                            outputValidator.validate(response.getContent(), expectedSchema);
                    if (result.isSuccess()) {
                        state.stop(TerminationPolicy.REASON_FINISHED);
                        break;
                    }
                    if (outputRetryCount >= maxRetries) {
                        state.stop(REASON_OUTPUT_INVALID);
                        break;
                    }
                    outputRetryCount++;
                    state.appendMessage(new Message(Role.USER,
                            "你输出的内容不是合法的 JSON：" + result.getError()
                                    + "。请重新输出，只输出符合要求的 JSON，不要有其他解释。",
                            null));
                    state.nextTurn();
                    continue;
                }
            }

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
