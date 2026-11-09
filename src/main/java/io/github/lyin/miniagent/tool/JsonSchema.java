package io.github.lyin.miniagent.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具参数的结构描述，JSON Schema 的一个最小子集：只支持 object 类型的
 * {@code properties} 与 {@code required}。
 *
 * <p>只实现子集是有意为之：真实的 JSON Schema 规范庞大，而工具参数只需要
 * 「声明有哪些字段、类型是什么、哪些必填」这三件事就足以支撑注册期校验与调用期转换。</p>
 */
public class JsonSchema {

    private final Map<String, Map<String, Object>> properties;
    private final List<String> required;

    private JsonSchema(Map<String, Map<String, Object>> properties, List<String> required) {
        this.properties = properties;
        this.required = required;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 转成模型可直接消费的外层结构：{@code {type: "object", properties: {...}, required: [...]}}。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        result.put("properties", properties);
        result.put("required", required);
        return result;
    }

    public Map<String, Map<String, Object>> getProperties() {
        return properties;
    }

    public List<String> getRequired() {
        return required;
    }

    /** 链式构造器。内部用有序容器，保证序列化结果稳定。 */
    public static class Builder {

        private final Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
        private final List<String> required = new ArrayList<>();

        public Builder addProperty(String name, String type, String description) {
            this.properties.put(name, Map.of("type", type, "description", description));
            return this;
        }

        public Builder required(String name) {
            this.required.add(name);
            return this;
        }

        public JsonSchema build() {
            return new JsonSchema(properties, required);
        }
    }
}
