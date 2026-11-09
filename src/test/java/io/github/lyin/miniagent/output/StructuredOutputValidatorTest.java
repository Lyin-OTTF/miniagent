package io.github.lyin.miniagent.output;

import io.github.lyin.miniagent.tool.JsonSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构化输出校验的三条路径：合规、缺必填字段、非法 JSON。
 *
 * <p>后两条都要求「不抛异常、返回失败结果」，这是它与工具参数校验的核心区别：
 * 答案不合规是可恢复状态，应由上层决定重试或降级。</p>
 */
class StructuredOutputValidatorTest {

    private final StructuredOutputValidator validator = new StructuredOutputValidator();

    private static JsonSchema answerSchema() {
        return JsonSchema.builder()
                .addProperty("answer", "string", "最终答案")
                .required("answer")
                .build();
    }

    @Test
    void 合法JSON_应该校验通过() {
        StructuredOutputValidator.OutputResult result = validator.validate(
                "{\"answer\": \"你好\"}", answerSchema());

        assertTrue(result.isSuccess());
        assertEquals("你好", result.getData().get("answer"));
    }

    @Test
    void 缺必填字段_应该校验失败() {
        StructuredOutputValidator.OutputResult result = validator.validate("{}", answerSchema());

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }

    @Test
    void 非法JSON_应该校验失败() {
        StructuredOutputValidator.OutputResult result = validator.validate("{坏的}", answerSchema());

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
    }
}
