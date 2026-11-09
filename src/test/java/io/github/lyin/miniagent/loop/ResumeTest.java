package io.github.lyin.miniagent.loop;

import io.github.lyin.miniagent.checkpoint.CheckpointStore;
import io.github.lyin.miniagent.checkpoint.HitlGate;
import io.github.lyin.miniagent.checkpoint.SideEffectLedger;
import io.github.lyin.miniagent.llm.LlmClient;
import io.github.lyin.miniagent.llm.mock.MockLlmClient;
import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.FunctionCall;
import io.github.lyin.miniagent.model.Message;
import io.github.lyin.miniagent.tool.JsonSchema;
import io.github.lyin.miniagent.tool.SchemaValidator;
import io.github.lyin.miniagent.tool.Tool;
import io.github.lyin.miniagent.tool.ToolRegistry;
import io.github.lyin.miniagent.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.lyin.miniagent.model.ChatCompletionResponse.answer;
import static io.github.lyin.miniagent.model.ChatCompletionResponse.toolCall;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResumeTest {

    static class CrashingLlmClient implements LlmClient {

        private final LlmClient delegate;
        private final int failAtCall;
        private int calls = 0;

        CrashingLlmClient(LlmClient delegate, int failAtCall) {
            this.delegate = delegate;
            this.failAtCall = failAtCall;
        }

        @Override
        public ChatCompletionResponse chat(List<Message> messages, List<Tool> tools) {
            if (++calls == failAtCall) {
                throw new RuntimeException("模拟崩溃");
            }
            return delegate.chat(messages, tools);
        }
    }

    @Test
    void 中断后续跑_写工具不重复执行() {
        AtomicInteger queryCount = new AtomicInteger(0);
        AtomicInteger deductCount = new AtomicInteger(0);
        AtomicInteger notifyCount = new AtomicInteger(0);

        JsonSchema emptySchema = JsonSchema.builder().build();

        Tool queryTool = countingTool("query_order", "查询订单", emptySchema, queryCount, "订单已查询");
        Tool deductTool = countingTool("deduct_stock", "扣减库存", emptySchema, deductCount, "库存已扣减");
        Tool notifyTool = countingTool("notify", "通知", emptySchema, notifyCount, "通知已发送");

        ToolRegistry registry = new ToolRegistry();
        registry.register(queryTool);
        registry.register(deductTool);
        registry.register(notifyTool);

        List<ChatCompletionResponse> script = List.of(
                toolCall(new FunctionCall("call-1", "query_order", "{}")),
                toolCall(new FunctionCall("call-2", "deduct_stock", "{}")),
                toolCall(new FunctionCall("call-3", "deduct_stock", "{}")),
                toolCall(new FunctionCall("call-4", "notify", "{}")),
                answer("完成")
        );
        MockLlmClient mockLlm = new MockLlmClient(script);
        LlmClient crashingLlm = new CrashingLlmClient(mockLlm, 3);

        CheckpointStore store = new CheckpointStore();
        AgentLoop loop = new AgentLoop(
                crashingLlm, registry, new TerminationPolicy(10, 5),
                new SchemaValidator(), new SideEffectLedger(), new HitlGate(),
                store, "test-session"
        );

        try {
            loop.run("帮我处理订单");
        } catch (RuntimeException e) {
            // 预期崩溃
        }

        assertEquals(1, queryCount.get(), "崩溃前 query 应执行一次");
        assertEquals(1, deductCount.get(), "崩溃前 deduct 应执行一次");
        assertEquals(2, mockLlm.getTurn(), "前提：崩溃发生在取剧本之前，[2]deduct 尚未被消费");

        AgentState resumed = loop.resume("test-session");

        assertEquals(1, deductCount.get(), "关键：账本拦住了重放，deduct 仍只执行一次");
        assertEquals(1, notifyCount.get(), "续跑中未执行过的工具应正常执行");
        assertEquals(TerminationPolicy.REASON_FINISHED, resumed.getStopReason());
    }

    private static Tool countingTool(String name, String description, JsonSchema schema,
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
}
