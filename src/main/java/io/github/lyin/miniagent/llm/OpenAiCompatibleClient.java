package io.github.lyin.miniagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.FunctionCall;
import io.github.lyin.miniagent.model.Message;
import io.github.lyin.miniagent.tool.JsonSchema;
import io.github.lyin.miniagent.tool.Tool;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenAiCompatibleClient implements LlmClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiCompatibleClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ChatCompletionResponse chat(List<Message> messages, List<Tool> tools) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", serializeMessages(messages));
        requestBody.put("tools", serializeTools(tools));

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            throw new RuntimeException("请求体序列化失败", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("请求被中断", e);
        } catch (IOException e) {
            throw new RuntimeException("网络请求失败: " + e.getMessage(), e);
        }

        if (response.statusCode() >= 400) {
            throw new RuntimeException("API 返回错误 " + response.statusCode() + ": " + response.body());
        }

        Map<String, Object> respMap;
        try {
            respMap = objectMapper.readValue(response.body(), Map.class);
        } catch (Exception e) {
            throw new RuntimeException("响应解析失败", e);
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
        Map<String, Object> choice0 = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice0.get("message");

        Object toolCalls = message.get("tool_calls");
        if (toolCalls != null) {
            List<Map<String, Object>> calls = (List<Map<String, Object>>) toolCalls;
            Map<String, Object> firstCall = calls.get(0);
            String callId = (String) firstCall.get("id");
            Map<String, Object> function = (Map<String, Object>) firstCall.get("function");
            String name = (String) function.get("name");
            String arguments = (String) function.get("arguments");
            return ChatCompletionResponse.toolCall(new FunctionCall(callId, name, arguments));
        }

        String content = (String) message.get("content");
        return ChatCompletionResponse.answer(content);
    }

    private List<Map<String, Object>> serializeMessages(List<Message> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("role", msg.getRole().wire());

            if (msg.getContent() != null) {
                map.put("content", msg.getContent());
            }

            if (msg.getToolCall() != null) {
                FunctionCall fc = msg.getToolCall();
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", fc.getName());
                function.put("arguments", fc.getArguments());

                Map<String, Object> call = new LinkedHashMap<>();
                if (fc.getId() != null) {
                    call.put("id", fc.getId());
                }
                call.put("type", "function");
                call.put("function", function);

                List<Map<String, Object>> toolCalls = new ArrayList<>();
                toolCalls.add(call);
                map.put("tool_calls", toolCalls);
            }

            if (msg.getToolCallId() != null) {
                map.put("tool_call_id", msg.getToolCallId());
            }

            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> serializeTools(List<Tool> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Tool tool : tools) {
            JsonSchema schema = tool.schema();
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", schema.toMap());

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "function");
            entry.put("function", function);
            result.add(entry);
        }
        return result;
    }
}
