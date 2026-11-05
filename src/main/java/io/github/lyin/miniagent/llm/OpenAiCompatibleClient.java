package io.github.lyin.miniagent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.FunctionCall;
import io.github.lyin.miniagent.model.Message;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//真实 LLM 客户端
public class OpenAiCompatibleClient implements LlmClient {

    //成员变量（5 个）
    // 1. baseUrl：API 地址，比如 "https://api.deepseek.com"
    private final String baseUrl;
    // 2. apiKey：密钥，比如 "sk-xxx"
    private final String apiKey;
    // 3. model：模型名，比如 "deepseek-chat"
    private final String model;
    // 4. httpClient：Java 内置 HTTP 客户端
    private final HttpClient httpClient;
    // 5. objectMapper：Jackson，你已经会了
    private final ObjectMapper objectMapper = new ObjectMapper();


    //构造器：接收前 3 个参数，后两个在构造器里 new 出来
    public OpenAiCompatibleClient(String baseUrl, String apiKey, String model){
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClient.newHttpClient();
    }


    @Override
    public ChatCompletionResponse chat(List<Message> messages) {
        //先造一个外层 Map
        HashMap<String, Object> requestBody = new HashMap<>();
        requestBody.put("model",this.model);

        //把List<Message> 转成 List<Map>
        List<Map<String, Object>> messageList = new ArrayList<>();
        for (Message msg : messages){
            Map<String,Object> msgMap = new HashMap<>();
            msgMap.put("role",msg.getRole().wire());
            msgMap.put("content",msg.getContent());
            messageList.add(msgMap);
        }
        requestBody.put("messages",messageList);

        //用 Jackson 把 Map 转成 JSON 字符串
        String jackBody;
        try{
            jackBody = this.objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            throw new RuntimeException("请求体序列化失败",e);
        }

        // 1. 拼 URL：baseUrl + "/chat/completions"
        String url = this.baseUrl + "/chat/completions";

// 2. 构造 HttpRequest
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + this.apiKey)  // 注意空格！
                .POST(HttpRequest.BodyPublishers.ofString(jackBody))
                .build();

// 3. 发送请求
        HttpResponse<String> response;
        try {
            response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        // 1. 把响应 JSON 字符串解析成 Map
        Map<String, Object> respMap;
        try {
            respMap = this.objectMapper.readValue(response.body(), Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

// 2. 取 choices 列表（它是个 List）
        List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
        Map<String, Object> choice0 = choices.get(0);

// 3. 取 message
        Map<String, Object> message = (Map<String, Object>) choice0.get("message");

// 4. 判断有没有 tool_calls
        Object toolCalls = message.get("tool_calls");
        if (toolCalls != null) {
            // 有工具调用 → 取 name 和 arguments
            List<Map<String, Object>> calls = (List<Map<String, Object>>) toolCalls;
            Map<String, Object> function = (Map<String, Object>) ((Map<String, Object>) calls.get(0)).get("function");
            String name = (String) function.get("name");
            String arguments = (String) function.get("arguments");
            return ChatCompletionResponse.toolCall(new FunctionCall(name, arguments));
        } else {
            // 普通回答 → 取 content
            String content = (String) message.get("content");
            return ChatCompletionResponse.answer(content);
        }
    }
}
