package io.github.lyin.miniagent.output;

import io.github.lyin.miniagent.tool.JsonSchema;
import io.github.lyin.miniagent.tool.SchemaValidator;

import java.util.Map;

public class StructuredOutputValidator {
    // 复用阶段 4 写好的 SchemaValidator
    private final SchemaValidator schemaValidator = new SchemaValidator();

    /**
     * 校验模型最终输出的 JSON 是否满足期望结构。
     * 和参数校验不同：失败不抛异常，而是返回一个「校验结果」对象，
     * 让上层决定是重试还是降级。
     */
    public OutputResult validate(String content, JsonSchema expectedSchema) {
        // schemaValidator.validate(content, expectedSchema)
        //    包在 try-catch 里，失败不抛异常，返回失败结果
        try{
            Map<String, Object> validate = schemaValidator.validate(content, expectedSchema);
            return OutputResult.ok(validate);
        } catch (Exception e) {
            return OutputResult.fail(e.getMessage());
        }

        // 🔴 第 2 步：成功返回 OutputResult.ok(map)
        //    失败返回 OutputResult.fail("错误信息")
        /*
        if () {
            return OutputResult.ok(validate);
        }
        else{
            throw new RuntimeException(OutputResult.fail("错误信息").toString());
        }

        return null; // 先留空
        */
    }

    // 内部类：校验结果（成功/失败 + 数据）
    public static class OutputResult {
        // 🔴 成员变量：boolean success, Map<String,Object> data, String error
        private final boolean success;
        private final Map<String, Object> data;
        private final String error;

        public OutputResult(boolean success, Map<String, Object> data, String error) {
            this.success = success;
            this.data = data;
            this.error = error;
        }

        // 🔴 静态工厂：ok(Map) / fail(String)
        public static OutputResult ok(Map<String, Object> data) {
            return new OutputResult(true, data, null);
        }
        public static OutputResult fail(String error) {
            return new OutputResult(false, null, error);
        }
        // 🔴 getter：isSuccess(), getData(), getError()
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
