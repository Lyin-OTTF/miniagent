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

/**
 * 续跑语义的端到端验证：中断恢复后，已执行过的写工具不得重复执行。
 *
 * <p>这是整个项目最关键的一条断言。它之所以成立，依赖两个前提，缺一不可：</p>
 * <ol>
 *   <li>崩溃必须发生在 {@code delegate.chat} <b>之前</b>，这样剧本里"第二次 deduct"
 *       那段没有被消费，续跑时才会再次出现同一个写工具调用；</li>
 *   <li>同一工具 + 同一参数的幂等键必须稳定，账本才能命中。</li>
 * </ol>
 * <p>前提 1 由 {@code assertEquals(2, mockLlm.getTurn())} 显式锁定——若哪天崩溃点被挪到
 * 消费剧本之后，这条断言会先失败，而不是让测试静默失去证明力。</p>
 */
public class ResumeTest {

    /** 崩溃装饰器：第 N 次 chat 抛异常，模拟进程崩溃（先抛后取，剧本不会被消费）。 */
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

        // 剧本：[0]query [1]deduct [2]deduct(续跑时重现) [3]notify [4]answer
        List<ChatCompletionResponse> script = List.of(
                toolCall(new FunctionCall("query_order", "{}")),
                toolCall(new FunctionCall("deduct_stock", "{}")),
                toolCall(new FunctionCall("deduct_stock", "{}")),
                toolCall(new FunctionCall("notify", "{}")),
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

        // 第 3 次 chat 崩溃：query、deduct 已执行并记账，剧本只消费到 [1]
        try {
            loop.run("帮我处理订单");
        } catch (RuntimeException e) {
            // 预期崩溃
        }

        assertEquals(1, queryCount.get(), "崩溃前 query 应执行一次");
        assertEquals(1, deductCount.get(), "崩溃前 deduct 应执行一次");
        assertEquals(2, mockLlm.getTurn(), "前提：崩溃发生在取剧本之前，[2]deduct 尚未被消费");

        // 续跑：模型重新请求 deduct，账本命中 → 跳过执行；随后 notify 正常执行
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
