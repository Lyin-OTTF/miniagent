package io.github.lyin.miniagent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchemaValidator {

    // Jackson 的核心对象，负责 JSON ↔ Java 对象互转
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 把模型返回的 arguments JSON 字符串解析成 Map，并校验 required 字段。
     */
    public Map<String, Object> validate(String argumentsJson, JsonSchema schema) {

        //JSON 字符串 → Map
        // 用 objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {})
        // 注意：readValue 会抛异常，要用 try-catch 包起来
        // 解析失败就 throw new IllegalArgumentException("参数不是合法JSON")
        Map<String, Object> args;
        try {
            args = objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("参数不是合法JSON: " + e.getMessage());
        }


        //校验 required
         for (String field : schema.getRequired()) {
             if (!args.containsKey(field)) {
                 throw new IllegalArgumentException("缺少必填参数: " + field);
             }
         }

        //返回解析好的 Map
        return args;
    }
}
