package io.github.lyin.miniagent.tool;

import java.util.*;

public class JsonSchema {
    private final Map<String, Map<String,Object>> properties;
    private final List<String> required;

    private JsonSchema(Map<String, Map<String, Object>> properties, List<String>required){
        this.properties = properties;
        this.required = required;
    }

    // 转成外层 Map：{type: "object", properties: {...}, required: [...]}
    public Map<String,Object>toMap(){
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        result.put("properties", properties);
        result.put("required", required);
        return result;
    }

    //静态模板工厂，两个geeter
    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Map<String, Object>> getProperties() {
        return properties;
    }

    public List<String> getRequired() {
        return required;
    }

    public static class Builder {
        private final Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
        private final List<String> required = new ArrayList<>();

        // 加一个参数
        public Builder addProperty(String name, String type, String description) {
            // 你写：往 properties 里放一个 entry
            // key = name
            // value = 一个 Map，里面放 "type" -> type, "description" -> description
            // 提示：Map.of("type", type, "description", description) 可以快速构造
            this.properties.put(name,Map.of("type", type, "description", description));
            return this;  // 链式调用必须 return this
        }

        // 标记必填
        public Builder required(String name) {
            // 你写：把 name 加到 required 列表里
            this.required.add(name);
            return this;
        }

        // build
        public JsonSchema build() {
            // 你写：new JsonSchema(properties, required)
            // 注意：这里直接传进去就行，因为 Builder 里的 properties/required
            //       已经是 LinkedHashMap/ArrayList 了
            return new JsonSchema(properties, required);
        }
    }
}
