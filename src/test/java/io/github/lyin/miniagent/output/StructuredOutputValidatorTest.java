package io.github.lyin.miniagent.output;

import io.github.lyin.miniagent.tool.JsonSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructuredOutputValidatorTest {

    private final StructuredOutputValidator validator = new StructuredOutputValidator();

    @Test
    void 合法JSON_应该校验通过() {
        // 🔴 1. 造一个期望 schema：必须有 answer（string）字段
        //    JsonSchema.builder()
        //        .addProperty("answer", "string", "最终答案")
        //        .required("answer")
        //        .build()
        JsonSchema schema = JsonSchema.builder()
            .addProperty("answer", "string", "最终答案")
            .required("answer")
            .build();

        // 🔴 2. 造一个合法 JSON：{"answer": "你好"}
        //    String json = "{\"answer\": \"你好\"}";
        String json = "{\"answer\": \"你好\"}";

        // 🔴 3. 调 validator.validate(json, schema)
        //    断言 result.isSuccess() == true
        //    断言 result.getData().get("answer") == "你好"
        StructuredOutputValidator.OutputResult result = validator.validate(json, schema);
        assertTrue(result.isSuccess());
        assertEquals("你好", result.getData().get("answer"));
    }

    @Test
    void 缺必填字段_应该校验失败() {
        // 🔴 1. 同样的 schema（要求 answer 必填）
        JsonSchema schema = JsonSchema.builder()
            .addProperty("answer", "string", "最终答案")
            .required("answer")
            .build();

        // 🔴 2. 造一个缺 answer 的 JSON：{}
        String json = "{}";

        // 🔴 3. 调 validate
        //    断言 result.isSuccess() == false
        //    断言 result.getError() 不为空
        StructuredOutputValidator.OutputResult result = validator.validate(json, schema);
        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    void 非法JSON_应该校验失败() {
        // 🔴 1. 随便一个 schema
        JsonSchema schema = JsonSchema.builder()
            .addProperty("answer", "string", "最终答案")
            .required("answer")
            .build();

        // 🔴 2. 造一个坏 JSON："{坏的"（不是合法 JSON）
        String json = "{坏的}";

        // 🔴 3. 调 validate
        //    断言 result.isSuccess() == false
        StructuredOutputValidator.OutputResult result = validator.validate(json, schema);
        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }
}
