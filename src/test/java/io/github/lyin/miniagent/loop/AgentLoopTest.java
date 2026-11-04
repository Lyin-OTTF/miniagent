package io.github.lyin.miniagent.loop;

import io.github.lyin.miniagent.llm.LlmClient;
import io.github.lyin.miniagent.llm.mock.MockLlmClient;
import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.FunctionCall;
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

class AgentLoopTest {

    // 一个假工具，名字叫 "query_order"，参数无所谓
    //在这里实现工具接口，不过为什么要用接口？
    //答：用接口顶替工具实现，避开现连数据库
    static class FakeTool implements Tool {
        //对外定义方法开始，返回对应值
        @Override public String name() { return "query_order"; }
        @Override public String description() { return "测试"; }
        @Override public io.github.lyin.miniagent.tool.JsonSchema schema() {
            return io.github.lyin.miniagent.tool.JsonSchema.builder().build();
        }
        @Override public ToolResult call(Map<String, Object> args) {
            return ToolResult.success("订单结果");
        }
    }

    @Test
    void 模型直接回答_应该finished() {
        //剧本：只有一条 answer，通过new一个答案实现，这里list.of是什么意思？
        //答：实现多个list.add
        MockLlmClient llm = new MockLlmClient(List.of(answer("你好，这是答案")));

        //注册假工具（空注册表也行，但放一个进去）
        ToolRegistry registry = new ToolRegistry();
        registry.register(new FakeTool());

        //终止策略：maxTurns=5, maxRepeat=3
        TerminationPolicy policy = new TerminationPolicy(5, 3);

        //跑
        AgentLoop loop = new AgentLoop(llm, registry, policy,new SchemaValidator());
        AgentState state = loop.run("帮我查订单");

        //断言，断言是什么意思？
        //答：`assertEquals(期望值, 实际值)` = "我预期是这个，如果不是就报错"。
        assertEquals("finished", state.getStopReason());
    }

    @Test
    void 反复调用同一工具_应该max_turns() {
        // 🔴 你写：剧本返回 5 条同一个 toolCall
        ChatCompletionResponse queryOrder = toolCall(new FunctionCall("query_order", "{}"));
        MockLlmClient llm = new MockLlmClient(List.of(queryOrder, queryOrder, queryOrder, queryOrder, queryOrder));

        // 🔴 你写：policy 的 maxTurns=2, maxRepeat=10（让它不触发死循环，只触发轮次超限）
        TerminationPolicy policy = new TerminationPolicy(2, 10);

        ToolRegistry registry = new ToolRegistry();
        registry.register(new FakeTool());

        // 🔴 你写：跑 run()，断言 stopReason == "max_turns"
        AgentLoop loop = new AgentLoop(llm,registry,policy,new SchemaValidator());
        AgentState state = loop.run("帮我查订单");
        assertEquals("max_turns", state.getStopReason());
    }

    @Test
    void 连续相同工具调用_应该loop_detected() {
        // 🔴 你写：剧本 3 条同一个 toolCall（同名字同参数）
        ChatCompletionResponse queryOrder = toolCall(new FunctionCall("query_order", "{}"));
        MockLlmClient llm = new MockLlmClient(List.of(queryOrder, queryOrder, queryOrder));

        //    maxRepeat=3, maxTurns=10（让它不触发轮次超限）
        TerminationPolicy policy = new TerminationPolicy(10, 3);

        ToolRegistry registry = new ToolRegistry();
        registry.register(new FakeTool());

        // 🔴 你写：跑 run()，断言 stopReason == "loop_detected"
        AgentLoop loop = new AgentLoop(llm,registry,policy,new SchemaValidator());
        AgentState state = loop.run("帮我查订单");
        assertEquals("loop_detected", state.getStopReason());
    }
}
