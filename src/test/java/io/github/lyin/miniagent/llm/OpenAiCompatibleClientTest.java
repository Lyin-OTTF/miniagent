package io.github.lyin.miniagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.FunctionCall;
import io.github.lyin.miniagent.model.Message;
import io.github.lyin.miniagent.model.Role;
import io.github.lyin.miniagent.tool.JsonSchema;
import io.github.lyin.miniagent.tool.Tool;
import io.github.lyin.miniagent.tool.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private final List<String> capturedBodies = Collections.synchronizedList(new ArrayList<>());
    private volatile String responseBody = "{}";
    private volatile int responseStatus = 200;
    private OpenAiCompatibleClient client;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            capturedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        client = new OpenAiCompatibleClient("http://127.0.0.1:" + port, "test-key", "deepseek-chat");
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private JsonNode lastRequest() throws IOException {
        assertFalse(capturedBodies.isEmpty(), "桩没有收到任何请求");
        return MAPPER.readTree(capturedBodies.get(capturedBodies.size() - 1));
    }

    @Test
    void 出站请求应携带完整的工具调用协议字段() throws IOException {
        responseBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"好\"},"
                + "\"finish_reason\":\"stop\"}]}";

        List<Message> messages = List.of(
                new Message(Role.USER, "帮我扣 A1 的库存", null),
                new Message(Role.ASSISTANT, null,
                        new FunctionCall("call_prev", "deduct_stock", "{\"sku\":\"A1\"}")),
                new Message(Role.TOOL, "库存已扣减", null, "call_prev")
        );

        client.chat(messages, List.of(new DeductStockTool()));

        JsonNode body = lastRequest();

        // 断言 1：model 和 tools 长度
        assertEquals("deepseek-chat", body.at("/model").asText());
        assertEquals(1, body.at("/tools").size());

        // 断言 2：工具 function 三要素
        assertEquals("deduct_stock", body.at("/tools/0/function/name").asText());
        assertEquals("扣减指定 SKU 的库存", body.at("/tools/0/function/description").asText());
        assertEquals("function", body.at("/tools/0/type").asText());

        // 断言 3：parameters 结构
        assertEquals("object", body.at("/tools/0/function/parameters/type").asText());
        JsonNode requiredNode = body.at("/tools/0/function/parameters/required");
        assertTrue(requiredNode.isArray(), "required 必须是数组；退化成字符串时模型侧无法按规范解析");
        assertEquals(1, requiredNode.size());
        assertEquals("sku", requiredNode.get(0).asText());
        assertEquals("string", body.at("/tools/0/function/parameters/properties/sku/type").asText());

        // 断言 4：assistant 消息的 tool_calls
        assertEquals("call_prev", body.at("/messages/1/tool_calls/0/id").asText());
        assertEquals("function", body.at("/messages/1/tool_calls/0/type").asText());
        assertEquals("deduct_stock", body.at("/messages/1/tool_calls/0/function/name").asText());
        JsonNode argsNode = body.at("/messages/1/tool_calls/0/function/arguments");
        assertTrue(argsNode.isTextual(), "arguments 必须是字符串，不能是对象");
        assertEquals("{\"sku\":\"A1\"}", argsNode.asText());

        // 断言 5：tool 消息带 tool_call_id
        assertEquals("tool", body.at("/messages/2/role").asText());
        assertEquals("call_prev", body.at("/messages/2/tool_call_id").asText());
        assertEquals("库存已扣减", body.at("/messages/2/content").asText());
    }

    @Test
    void 入站响应应把工具调用id解析进FunctionCall() throws IOException {
        responseBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"call_xyz\",\"type\":\"function\","
                + "\"function\":{\"name\":\"deduct_stock\",\"arguments\":\"{\\\"sku\\\":\\\"A1\\\"}\"}}]},"
                + "\"finish_reason\":\"tool_calls\"}]}";

        ChatCompletionResponse response = client.chat(
                List.of(new Message(Role.USER, "扣库存", null)), List.of(new DeductStockTool()));

        // 断言 6
        assertTrue(response.hasToolCall());
        FunctionCall fc = response.getToolCall();
        assertNotNull(fc);
        assertEquals("call_xyz", fc.getId());
        assertEquals("deduct_stock", fc.getName());
        assertEquals("{\"sku\":\"A1\"}", fc.getArguments());
        assertNull(response.getContent());
    }

    @Test
    void 响应为最终答案时应返回内容且无工具调用() throws IOException {
        responseBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"订单 123 已发货\"},"
                + "\"finish_reason\":\"stop\"}]}";

        ChatCompletionResponse response = client.chat(
                List.of(new Message(Role.USER, "查订单", null)), List.of(new DeductStockTool()));

        // 断言 7
        assertFalse(response.hasToolCall());
        assertEquals("订单 123 已发货", response.getContent());
    }

    @Test
    void 上游返回错误状态码时应抛出带状态码的异常() {
        responseStatus = 400;
        responseBody = "{\"error\":{\"message\":\"invalid tool_call_id\"}}";

        // 断言 8
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                client.chat(List.of(new Message(Role.USER, "查订单", null)), List.of()));
        assertTrue(ex.getMessage().contains("400"));
        assertTrue(ex.getMessage().contains("invalid tool_call_id"));
    }

    private static final class DeductStockTool implements Tool {

        @Override
        public String name() {
            return "deduct_stock";
        }

        @Override
        public String description() {
            return "扣减指定 SKU 的库存";
        }

        @Override
        public JsonSchema schema() {
            return JsonSchema.builder()
                    .addProperty("sku", "string", "商品 SKU")
                    .required("sku")
                    .build();
        }

        @Override
        public ToolResult call(Map<String, Object> args) {
            return ToolResult.success("库存已扣减");
        }
    }
}
