package io.github.lyin.miniagent.output;

import io.github.lyin.miniagent.tool.JsonSchema;
import io.github.lyin.miniagent.tool.SchemaValidator;

import java.util.Map;

/**
 * 最终答案的结构化输出校验器。
 *
 * <p>与工具参数校验的关键差异在于失败语义：参数非法必须抛异常打断调用链，
 * 而答案结构不合规是「模型没按约定输出」，属于可恢复状态——
 * 上层通常选择重试或降级，而不是让整个会话崩掉。因此这里吞掉异常并返回失败结果对象。</p>
 */
public class StructuredOutputValidator {

    /** 复用工具参数校验的同一套 Schema 规则，避免两份实现漂移。 */
    private final SchemaValidator schemaValidator = new SchemaValidator();

    /**
     * 校验模型最终输出的 JSON 是否满足期望结构。
     *
     * @param content        模型输出的原始文本，应为 JSON 对象
     * @param expectedSchema 期望的结构
     * @return 成功携带解析后的 Map，失败携带错误信息，不抛异常
     */
    public OutputResult validate(String content, JsonSchema expectedSchema) {
        try {
            Map<String, Object> data = schemaValidator.validate(content, expectedSchema);
            return OutputResult.ok(data);
        } catch (RuntimeException e) {
            return OutputResult.fail(e.getMessage());
        }
    }

    /** 校验结果：成功/失败 + 解析数据 + 错误信息。 */
    public static class OutputResult {

        private final boolean success;
        private final Map<String, Object> data;
        private final String error;

        public OutputResult(boolean success, Map<String, Object> data, String error) {
            this.success = success;
            this.data = data;
            this.error = error;
        }

        public static OutputResult ok(Map<String, Object> data) {
            return new OutputResult(true, data, null);
        }

        public static OutputResult fail(String error) {
            return new OutputResult(false, null, error);
        }

        public boolean isSuccess() {
            return success;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public String getError() {
            return error;
        }
    }
}
