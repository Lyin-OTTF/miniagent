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

public class AgentLoop {
    private final LlmClient llmClient;
    private final ToolRegistry registry;
    private final TerminationPolicy terminationPolicy;
    private final SchemaValidator schemaValidator;
    private final SideEffectLedger ledger;
    private final HitlGate hitlGate;
    private final CheckpointStore checkpointStore;
    private final String sessionId;

    public AgentLoop(LlmClient llmClient, ToolRegistry registry, TerminationPolicy terminationPolicy, SchemaValidator schemaValidator, SideEffectLedger ledger, HitlGate hitlGate, CheckpointStore checkpointStore, String sessionId) {
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
     * 跑一轮 Agent。
     * @param userInput 用户输入的文字
     * @return 最终 AgentState（包含完整消息历史和终止原因）
     */
    public AgentState run(String userInput) {
        AgentState state = new AgentState();

        // 把用户消息追加到历史
        state.appendMessage(new Message(Role.USER, userInput, null));

        //while 循环
        while (true){
            //问模型
            ChatCompletionResponse response = this.llmClient.chat(state.getMessages());

            Message assistantMsg;
            if (response.hasToolCall()) {
                assistantMsg = new Message(Role.ASSISTANT, null, response.getToolCall());
            } else {
                assistantMsg = new Message(Role.ASSISTANT, response.getContent(), null);
            }
            state.appendMessage(assistantMsg);

            //判断该不该停
            String stopReason = this.terminationPolicy.check(state, response);
            if (stopReason != null) {
                state.stop(stopReason);
                break;
            }
            //模型要调工具
            FunctionCall fc = response.getToolCall();
            Tool tool = this.registry.get(fc.getName());
            //调用写过的这个方法，转json，顺带校验一下格式，argument是存着的大模型json参数
            Map<String, Object> args = this.schemaValidator.validate(fc.getArguments(), tool.schema());
            String key = IdempotencyKey.of(tool.name(), args);
            ToolResult result = this.ledger.lookup(key);
            if (result == null) {
                // 🔴 账本没命中，先问人要不要放行
                if (this.hitlGate.approve(tool, args)) {
                    // 人同意了，真正执行
                    result = tool.call(args);
                    this.ledger.record(key, result);
                } else {
                    // 人拒绝了，返回一个失败结果
                    result = ToolResult.failure("用户拒绝执行: " + tool.name());
                }
            }
            //把工具结果作为 tool 消息追加到历史
            state.appendMessage(new Message(Role.TOOL, result.getText(), null));

            //轮次 +1
            state.nextTurn();

            //存快照
            Checkpoint checkpoint = Checkpoint.from(state, this.ledger);
            this.checkpointStore.save(this.sessionId, checkpoint);
        }
        return state;
    }
}
