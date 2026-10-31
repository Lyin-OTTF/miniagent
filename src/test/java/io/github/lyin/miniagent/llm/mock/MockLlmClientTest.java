package io.github.lyin.miniagent.llm.mock;

import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.FunctionCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MockLlmClientTest {

    @Test
    void chat_按剧本顺序返回() {
        ChatCompletionResponse 第一轮 = ChatCompletionResponse.toolCall(
                new FunctionCall("query_order", "{\"orderId\":123}")
        );
        ChatCompletionResponse 第二轮 = ChatCompletionResponse.answer("任务完成");

        MockLlmClient client = new MockLlmClient(List.of(第一轮, 第二轮));

        ChatCompletionResponse r1 = client.chat(List.of());
        assertTrue(r1.hasToolCall());
        assertEquals("query_order", r1.getToolCall().getName());

        ChatCompletionResponse r2 = client.chat(List.of());
        assertFalse(r2.hasToolCall());
        assertEquals("任务完成", r2.getContent());
    }
}
