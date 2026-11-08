package io.github.lyin.miniagent;

import io.github.lyin.miniagent.llm.mock.MockLlmClient;
import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.FunctionCall;
import io.github.lyin.miniagent.model.Message;
import io.github.lyin.miniagent.loop.AgentState;
import io.github.lyin.miniagent.tool.JsonSchema;
import io.github.lyin.miniagent.tool.Tool;
import io.github.lyin.miniagent.tool.ToolResult;

import java.util.List;
import java.util.Map;

import static io.github.lyin.miniagent.model.ChatCompletionResponse.answer;
import static io.github.lyin.miniagent.model.ChatCompletionResponse.toolCall;

public class Main {
    public static void main(String[] args) {
        // 1. 造一个 Mock LLM：先调工具，再回答
        ChatCompletionResponse callTool = toolCall(new FunctionCall("query_order", "{\"orderId\":\"123\"}"));
        ChatCompletionResponse finalAnswer = answer("订单123已查到，状态为已发货");
        MockLlmClient llm = new MockLlmClient(List.of(callTool, finalAnswer));

        // 2. 造一个假工具
        // （匿名类实现 Tool 接口）
        Tool tool = new Tool() {
            @Override public String name() { return "query_order"; }
            @Override public String description() { return "查询订单"; }
            @Override public JsonSchema schema() { return JsonSchema.builder().build(); }
            @Override public ToolResult call(Map<String, Object> args) {
                return ToolResult.success("订单123已查到，状态为已发货");
            }
        };
        // 3. 造 Agent，注册工具，跑
        Agent agent = new Agent(llm, "demo-session");
        agent.registerTool(tool);


        AgentState state = agent.run("帮我查一下订单123");

        // 4. 打印结果
        System.out.println("终止原因: " + state.getStopReason());
        System.out.println("消息历史:");
        for (Message msg : state.getMessages()) {
            System.out.println("  [" + msg.getRole().wire() + "] " + msg.getContent());
        }
    }
}
