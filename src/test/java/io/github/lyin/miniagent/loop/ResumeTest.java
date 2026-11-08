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

    // 崩溃装饰器：第 N 次 chat 抛异常，模拟进程崩溃
    static class CrashingLlmClient implements LlmClient {
        private final LlmClient delegate;
        private final int failAtCall;
        private int calls = 0;

        CrashingLlmClient(LlmClient delegate, int failAtCall) {
            this.delegate = delegate;
            this.failAtCall = failAtCall;
        }

        @Override
        public ChatCompletionResponse chat(List<Message> messages) {
            if (++calls == failAtCall) {
                throw new RuntimeException("模拟崩溃");
            }
            return delegate.chat(messages);
        }
    }

    @Test
    void 中断后续跑_写工具不重复执行() {
        // 1. 计数器
        AtomicInteger queryCount = new AtomicInteger(0);
        AtomicInteger deductCount = new AtomicInteger(0);
        AtomicInteger notifyCount = new AtomicInteger(0);

        JsonSchema emptySchema = JsonSchema.builder().build();

        // 2. 三个工具
        Tool queryTool = new Tool() {
            @Override public String name() { return "query_order"; }
            @Override public String description() { return "查询订单"; }
            @Override public JsonSchema schema() { return emptySchema; }
            @Override public ToolResult call(Map<String, Object> args) {
                queryCount.incrementAndGet();
                return ToolResult.success("订单已查询");
            }
        };
        Tool deductTool = new Tool() {
            @Override public String name() { return "deduct_stock"; }
            @Override public String description() { return "扣减库存"; }
            @Override public JsonSchema schema() { return emptySchema; }
            @Override public ToolResult call(Map<String, Object> args) {
                deductCount.incrementAndGet();
                return ToolResult.success("库存已扣减");
            }
        };
        Tool notifyTool = new Tool() {
            @Override public String name() { return "notify"; }
            @Override public String description() { return "通知"; }
            @Override public JsonSchema schema() { return emptySchema; }
            @Override public ToolResult call(Map<String, Object> args) {
                notifyCount.incrementAndGet();
                return ToolResult.success("通知已发送");
            }
        };

        // 3. 注册工具
        ToolRegistry registry = new ToolRegistry();
        registry.register(queryTool);
        registry.register(deductTool);
        registry.register(notifyTool);

        // 4. Mock 剧本：query → deduct → notify → 回答
        List<ChatCompletionResponse> script = List.of(
                toolCall(new FunctionCall("query_order", "{}")),
                toolCall(new FunctionCall("deduct_stock", "{}")),
                toolCall(new FunctionCall("deduct_stock", "{}")),   // 第二次 deduct
                answer("完成")
        );
        MockLlmClient mockLlm = new MockLlmClient(script);

        // 5. 崩溃客户端：第 3 次 chat 抛异常（第 3 次就是 notify 那次）
        LlmClient crashingLlm = new CrashingLlmClient(mockLlm, 3);

        // 6. 造 AgentLoop（8 个参数）
        TerminationPolicy policy = new TerminationPolicy(10, 5);
        CheckpointStore store = new CheckpointStore();
        AgentLoop loop = new AgentLoop(
                crashingLlm, registry, policy,
                new SchemaValidator(), new SideEffectLedger(), new HitlGate(),
                store, "test-session"
        );

        // 7. 跑 run()，预期第 3 次 chat 崩溃
        try {
            loop.run("帮我处理订单");
        } catch (RuntimeException e) {
            // 预期崩溃
        }

        // 8. 崩溃时：query 和 deduct 已经执行，notify 还没执行
        assertEquals(1, queryCount.get());
        assertEquals(1, deductCount.get());


        // 9. resume() 接着跑（用同一个 mockLlm，剧本还在继续）
        AgentState resumed = loop.resume("test-session");

        // 10. 关键断言：deduct 没重复执行，notify 执行了一次
        assertEquals(1, deductCount.get());  // 关键！账本拦住了
        assertEquals("finished", resumed.getStopReason());

    }
}
