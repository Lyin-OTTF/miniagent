package io.github.lyin.miniagent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolRegistryTest {
    // 一个假工具，专门用来测试注册表，不真的执行
    static class FakeTool implements Tool {
        private final String toolName;
        private final JsonSchema schema;

        FakeTool(String toolName, JsonSchema schema) {
            this.toolName = toolName;
            this.schema = schema;
        }

        @Override public String name() { return toolName; }
        @Override public String description() { return "测试工具"; }
        @Override public JsonSchema schema() { return schema; }
        @Override public ToolResult call(java.util.Map<String, Object> args) {
            return ToolResult.success("fake");
        }
    }

    @Test
    void 注册同名工具_应该抛异常() {
        ToolRegistry registry = new ToolRegistry();
        JsonSchema schema = JsonSchema.builder().build();

        registry.register(new FakeTool("query_order", schema));

        // 第二次注册同名工具，应该抛 IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () ->
                registry.register(new FakeTool("query_order", schema))
        );
    }

    @Test
    void 注册required未定义字段的工具_应该抛异常() {
        ToolRegistry registry = new ToolRegistry();
        // schema 声明了 required("orderId")，但 properties 里没定义 orderId
        JsonSchema badSchema = JsonSchema.builder()
                .required("orderId")
                .build();

        // 应该抛异常，因为 required 引用了不存在的字段
        assertThrows(IllegalArgumentException.class, () ->
                registry.register(new FakeTool("query_order", badSchema))
        );
    }
}
