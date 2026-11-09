package io.github.lyin.miniagent;

import io.github.lyin.miniagent.llm.LlmClient;
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
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.lyin.miniagent.model.ChatCompletionResponse.answer;
import static io.github.lyin.miniagent.model.ChatCompletionResponse.toolCall;

/**
 * 演示入口，用 Mock 剧本跑两段流程，不联网、不消耗额度。
 *
 * <p>第一段展示基本闭环：模型请求工具 → 执行 → 模型给出答案。
 * 第二段展示本项目最关键的能力：执行到一半崩溃、从快照续跑，
 * 且已经产生副作用的写工具不会被重复执行。</p>
 */
public class Main {

    public static void main(String[] args) {
        demoToolCallAndAnswer();
        demoResumeWithoutReplay();
    }

    /** 第一段：模型要调工具，执行后给出答案。 */
    private static void demoToolCallAndAnswer() {
        MockLlmClient llm = new MockLlmClient(List.of(
                toolCall(new FunctionCall("call-1", "query_order", "{\"orderId\":\"123\"}")),
                answer("订单 123 已查到，状态为已发货")));

        Agent agent = new Agent(llm, "demo-basic");
        agent.registerTool(tool("query_order", "查询订单",
                JsonSchema.builder().addProperty("orderId", "string", "订单号").required("orderId").build(),
                new AtomicInteger(), "订单 123 已查到，状态为已发货"));

        AgentState state = agent.run("帮我查一下订单 123");

        System.out.println("=== 演示一：工具调用闭环 ===");
        System.out.println("终止原因: " + state.getStopReason());
        printHistory(state);
        System.out.println();
    }

    /**
     * 第二段：第 3 次模型调用时模拟进程崩溃，随后从快照续跑。
     *
     * <p>剧本刻意让模型在续跑后「再次请求 deduct_stock」——若没有副作用账本，
     * 库存会被扣两次。这正是要演示的失败模式。</p>
     */
    private static void demoResumeWithoutReplay() {
        AtomicInteger queryCount = new AtomicInteger();
        AtomicInteger deductCount = new AtomicInteger();
        AtomicInteger notifyCount = new AtomicInteger();

        JsonSchema noArgs = JsonSchema.builder().build();
        List<ChatCompletionResponse> script = List.of(
                toolCall(new FunctionCall("call-1", "query_order", "{}")),
                toolCall(new FunctionCall("call-2", "deduct_stock", "{}")),
                toolCall(new FunctionCall("call-3", "deduct_stock", "{}")),
                toolCall(new FunctionCall("call-4", "notify", "{}")),
                answer("订单处理完成：库存已扣减，通知已发送"));

        MockLlmClient mock = new MockLlmClient(script);
        // 同一个 Mock 实例贯穿崩溃与续跑，剧本游标不会因新建客户端而重置
        LlmClient crashingLlm = new CrashAtCall(mock, 3);

        Agent agent = new Agent(crashingLlm, "demo-resume");
        agent.registerTool(tool("query_order", "查询订单", noArgs, queryCount, "订单已查询"));
        agent.registerTool(tool("deduct_stock", "扣减库存", noArgs, deductCount, "库存已扣减"));
        agent.registerTool(tool("notify", "发送通知", noArgs, notifyCount, "通知已发送"));

        System.out.println("=== 演示二：中断后续跑，写操作不重放 ===");
        try {
            agent.run("帮我处理订单");
            System.out.println("意外：本应在这里崩溃");
        } catch (RuntimeException e) {
            System.out.println("[1] 首次执行中断: " + e.getMessage());
        }
        System.out.println("    中断时累计执行次数: query_order=" + queryCount.get()
                + ", deduct_stock=" + deductCount.get()
                + ", notify=" + notifyCount.get());

        AgentState resumed = agent.resume();

        System.out.println("[2] 从快照续跑完成");
        System.out.println("    终止原因: " + resumed.getStopReason());
        System.out.println("    续跑后累计执行次数: query_order=" + queryCount.get()
                + ", deduct_stock=" + deductCount.get()
                + ", notify=" + notifyCount.get());
        System.out.println("    结论: 模型在续跑时再次请求了 deduct_stock，"
                + "账本判定该幂等键已执行并按历史结果回填，"
                + "故 deduct_stock 全程只执行了 " + deductCount.get() + " 次");
        printHistory(resumed);
    }

    private static void printHistory(AgentState state) {
        System.out.println("    消息历史:");
        for (Message msg : state.getMessages()) {
            String text = msg.getContent() != null ? msg.getContent()
                    : "[请求工具 " + msg.getToolCall().getName() + "]";
            System.out.println("      [" + msg.getRole().wire() + "] " + text);
        }
    }

    private static Tool tool(String name, String description, JsonSchema schema,
                             AtomicInteger counter, String resultText) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return description; }
            @Override public JsonSchema schema() { return schema; }
            @Override public ToolResult call(Map<String, Object> args) {
                counter.incrementAndGet();
                return ToolResult.success(resultText);
            }
        };
    }

    /** 在第 N 次模型调用时抛异常，模拟进程崩溃；崩溃发生在取剧本之前，该条剧本不会被消费。 */
    private static final class CrashAtCall implements LlmClient {

        private final LlmClient delegate;
        private final int failAtCall;
        private int calls;

        private CrashAtCall(LlmClient delegate, int failAtCall) {
            this.delegate = delegate;
            this.failAtCall = failAtCall;
        }

        @Override
        public ChatCompletionResponse chat(List<Message> messages, List<Tool> tools) {
            if (++calls == failAtCall) {
                throw new RuntimeException("模拟第 " + failAtCall + " 次模型调用时进程崩溃");
            }
            return delegate.chat(messages, tools);
        }
    }
}
