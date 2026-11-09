package io.github.lyin.miniagent.loop;

import io.github.lyin.miniagent.checkpoint.CheckpointStore;
import io.github.lyin.miniagent.checkpoint.HitlGate;
import io.github.lyin.miniagent.checkpoint.SideEffectLedger;
import io.github.lyin.miniagent.llm.mock.MockLlmClient;
import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.Role;
import io.github.lyin.miniagent.tool.JsonSchema;
import io.github.lyin.miniagent.tool.SchemaValidator;
import io.github.lyin.miniagent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.lyin.miniagent.model.ChatCompletionResponse.answer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredOutputTest {

    @Test
    void 第一次输出不合法_重试后通过_应该finished() {
        ChatCompletionResponse badAnswer = answer("这不是JSON");
        ChatCompletionResponse goodAnswer = answer("{\"result\":\"ok\",\"count\":42}");
        MockLlmClient llm = new MockLlmClient(List.of(badAnswer, goodAnswer));

        JsonSchema schema = JsonSchema.builder()
                .addProperty("result", "string", "结果")
                .addProperty("count", "integer", "数量")
                .required("result")
                .required("count")
                .build();

        ToolRegistry registry = new ToolRegistry();

        AgentLoop loop = new AgentLoop(llm, registry, new TerminationPolicy(10, 5),
                new SchemaValidator(), new SideEffectLedger(), new HitlGate(),
                new CheckpointStore(), "test-so-1", schema, 2);

        AgentState state = loop.run("帮我查结果");
        assertEquals("finished", state.getStopReason());

        // 只断言 stopReason 是不够的——把校验整段关掉后，非法答案也会直接 finished，测试照样绿。
        // 因此必须断言「机制确实跑过」：问了两次模型、且两次之间插入了带错误信息的纠正提示。
        assertEquals(2, llm.getTurn(), "校验失败后应再问一次模型，而不是把非法答案当结果");
        assertTrue(hasRetryPrompt(state), "必须把校验错误回填给模型，否则模型不知道要改什么");
        assertEquals("{\"result\":\"ok\",\"count\":42}",
                state.getMessages().get(state.getMessages().size() - 1).getContent(),
                "最终答案应是重试后那一份");
    }

    @Test
    void 连续输出不合法_用尽重试次数_应该output_invalid() {
        ChatCompletionResponse bad1 = answer("不是JSON");
        ChatCompletionResponse bad2 = answer("也不是JSON");
        MockLlmClient llm = new MockLlmClient(List.of(bad1, bad2, bad2));

        JsonSchema schema = JsonSchema.builder()
                .addProperty("result", "string", "结果")
                .required("result")
                .build();

        ToolRegistry registry = new ToolRegistry();

        // maxRetries=1：第一次失败后只能再试 1 次，第二次还失败就 output_invalid
        AgentLoop loop = new AgentLoop(llm, registry, new TerminationPolicy(10, 5),
                new SchemaValidator(), new SideEffectLedger(), new HitlGate(),
                new CheckpointStore(), "test-so-2", schema, 1);

        AgentState state = loop.run("帮我查结果");
        assertEquals("output_invalid", state.getStopReason());
        assertEquals(2, llm.getTurn(), "maxRetries=1 表示首次失败后还能再试一次，模型应被调用两次");
    }

    /** 重试时回填给模型的纠正提示必须带上校验错误原文。 */
    private static boolean hasRetryPrompt(AgentState state) {
        return state.getMessages().stream()
                .anyMatch(m -> Role.USER.equals(m.getRole())
                        && m.getContent() != null
                        && m.getContent().contains("不是合法的 JSON"));
    }
}
