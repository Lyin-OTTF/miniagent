package io.github.lyin.miniagent.loop;

import io.github.lyin.miniagent.checkpoint.CheckpointStore;
import io.github.lyin.miniagent.checkpoint.HitlGate;
import io.github.lyin.miniagent.checkpoint.SideEffectLedger;
import io.github.lyin.miniagent.llm.mock.MockLlmClient;
import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.FunctionCall;
import io.github.lyin.miniagent.tool.JsonSchema;
import io.github.lyin.miniagent.tool.SchemaValidator;
import io.github.lyin.miniagent.tool.Tool;
import io.github.lyin.miniagent.tool.ToolRegistry;
import io.github.lyin.miniagent.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.github.lyin.miniagent.model.ChatCompletionResponse.answer;
import static io.github.lyin.miniagent.model.ChatCompletionResponse.toolCall;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 终止条件的三条分支：模型主动结束、轮次超限、重复调用同一工具被判定为死循环。
 *
 * <p>用真实工具会引入数据库等外部依赖，测试因此只关心「循环在什么条件下停」，
 * 工具实现退化为返回固定字符串的替身。</p>
 */
class AgentLoopTest {

    static class FakeTool implements Tool {

        @Override public String name() { return "query_order"; }
        @Override public String description() { return "测试"; }
        @Override public JsonSchema schema() { return JsonSchema.builder().build(); }
        @Override public ToolResult call(Map<String, Object> args) { return ToolResult.success("订单结果"); }
    }

    @Test
    void 模型直接回答_应该finished() {
        MockLlmClient llm = new MockLlmClient(List.of(answer("你好，这是答案")));

        ToolRegistry registry = new ToolRegistry();
        registry.register(new FakeTool());

        AgentLoop loop = new AgentLoop(llm, registry, new TerminationPolicy(5, 3),
                new SchemaValidator(), new SideEffectLedger(), new HitlGate(),
                new CheckpointStore(), "test-session-1");
        AgentState state = loop.run("帮我查订单");

        assertEquals("finished", state.getStopReason());
    }

    @Test
    void 反复调用同一工具_应该max_turns() {
        ChatCompletionResponse queryOrder = toolCall(new FunctionCall("query_order", "{}"));
        MockLlmClient llm = new MockLlmClient(
                List.of(queryOrder, queryOrder, queryOrder, queryOrder, queryOrder));

        // maxRepeat 调高，隔离出「只有轮次上限生效」这一条分支
        TerminationPolicy policy = new TerminationPolicy(2, 10);

        ToolRegistry registry = new ToolRegistry();
        registry.register(new FakeTool());

        AgentLoop loop = new AgentLoop(llm, registry, policy, new SchemaValidator(),
                new SideEffectLedger(), new HitlGate(), new CheckpointStore(), "test-session-2");
        AgentState state = loop.run("帮我查订单");

        assertEquals("max_turns", state.getStopReason());
    }

    @Test
    void 连续相同工具调用_应该loop_detected() {
        ChatCompletionResponse queryOrder = toolCall(new FunctionCall("query_order", "{}"));
        MockLlmClient llm = new MockLlmClient(List.of(queryOrder, queryOrder, queryOrder));

        // maxTurns 调高，确保先命中重复检测而不是轮次上限
        TerminationPolicy policy = new TerminationPolicy(10, 3);

        ToolRegistry registry = new ToolRegistry();
        registry.register(new FakeTool());

        AgentLoop loop = new AgentLoop(llm, registry, policy, new SchemaValidator(),
                new SideEffectLedger(), new HitlGate(), new CheckpointStore(), "test-session-3");
        AgentState state = loop.run("帮我查订单");

        assertEquals("loop_detected", state.getStopReason());
    }
}
