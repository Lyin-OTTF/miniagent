package io.github.lyin.miniagent.loop;

import io.github.lyin.miniagent.llm.LlmClient;
import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.FunctionCall;
import io.github.lyin.miniagent.model.Message;
import io.github.lyin.miniagent.model.Role;
import io.github.lyin.miniagent.tool.Tool;
import io.github.lyin.miniagent.tool.ToolRegistry;
import io.github.lyin.miniagent.tool.ToolResult;

import java.util.Map;

public class AgentLoop {
    private final LlmClient llmClient;
    private final ToolRegistry registry;
    private final TerminationPolicy terminationPolicy;

    public AgentLoop(LlmClient llmClient, ToolRegistry registry, TerminationPolicy terminationPolicy) {
        this.llmClient = llmClient;
        this.registry = registry;
        this.terminationPolicy = terminationPolicy;
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
            ToolResult result = tool.call(Map.of());   // 参数解析是阶段4，现在先传空Map

            //把工具结果作为 tool 消息追加到历史
            state.appendMessage(new Message(Role.TOOL, result.getText(), null));

            //轮次 +1
            state.nextTurn();
        }
        return state;
    }
}
