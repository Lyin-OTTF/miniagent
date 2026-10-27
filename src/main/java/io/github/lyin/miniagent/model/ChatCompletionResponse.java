package io.github.lyin.miniagent.model;

/**
 * LLM 一次对话补全的响应：二选一，要么是最终答案 content，要么是工具调用 toolCall。
 * 通过私有构造器 + 静态工厂方法强制这个不变式，外部无法构造出"两者同时存在"的矛盾状态。
 */
public class ChatCompletionResponse {
    private final String content;
    private final FunctionCall toolCall;

    private ChatCompletionResponse(String content, FunctionCall toolCall) {
        this.content = content;
        this.toolCall = toolCall;
    }

    /** 最终答案：content 有值，toolCall 为 null。 */
    public static ChatCompletionResponse answer(String content) {
        return new ChatCompletionResponse(content, null);
    }

    /** 工具调用：toolCall 有值，content 为 null。 */
    public static ChatCompletionResponse toolCall(FunctionCall toolCall) {
        return new ChatCompletionResponse(null, toolCall);
    }

    /** 本轮是否要调工具：Agent Loop 靠它判断继续循环还是终止。 */
    public boolean hasToolCall() {
        return toolCall != null;
    }

    public String getContent() {
        return content;
    }

    public FunctionCall getToolCall() {
        return toolCall;
    }
}
