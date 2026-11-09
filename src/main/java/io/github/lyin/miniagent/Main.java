package io.github.lyin.miniagent;

import io.github.lyin.miniagent.llm.mock.MockLlmClient;
import io.github.lyin.miniagent.loop.AgentState;
import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.FunctionCall;
import io.github.lyin.miniagent.model.Message;
import io.github.lyin.miniagent.tool.JsonSchema;
import io.github.lyin.miniagent.tool.Tool;
import io.github.lyin.miniagent.tool.ToolResult;

import java.util.List;
import java.util.Map;

import static io.github.lyin.miniagent.model.ChatCompletionResponse.answer;
import static io.github.lyin.miniagent.model.ChatCompletionResponse.toolCall;

/**
 * 演示入口：用 Mock LLM 跑一次完整的「模型要调工具 → 执行工具 → 模型给出答案」流程。
 * 不联网、不花钱，适合本地验证运行时是否正常。
 */
public class Main {
    public static void main(String[] args) {
        ChatCompletionResponse callTool = toolCall(new FunctionCall("query_order", "{\"orderId\":\"123\"}"));
        ChatCompletionResponse finalAnswer = answer("订单 123 已查到，状态为已发货");
        MockLlmClient llm = new MockLlmClient(List.of(callTool, finalAnswer));

        Tool tool = new Tool() {
            @Override public String name() { return "query_order"; }
            @Override public String description() { return "查询订单"; }
            @Override public JsonSchema schema() {
                return JsonSchema.builder()
                        .addProperty("orderId", "string", "订单号")
                        .required("orderId")
                        .build();
            }
            @Override public ToolResult call(Map<String, Object> args) {
                return ToolResult.success("订单 123 已查到，状态为已发货");
            }
        };

        Agent agent = new Agent(llm, "demo-session");
        agent.registerTool(tool);

        AgentState state = agent.run("帮我查一下订单 123");

        System.out.println("终止原因: " + state.getStopReason());
        System.out.println("消息历史:");
        for (Message msg : state.getMessages()) {
            System.out.println("  [" + msg.getRole().wire() + "] " + msg.getContent());
        }
    }
}
