package io.github.lyin.miniagent.model;

/**
 * 对话中的一条消息，不可变值对象。
 *
 * <p>字段的可空组合由 role 决定：{@code assistant} 决定调用工具时 content 为 null、
 * toolCall 非 null；{@code tool} 回填执行结果时 content 非 null、toolCall 为 null。
 * 不做构造期强校验，是因为真实模型响应本身就存在两者兼有的情况。</p>
 */
public class Message {

    private final Role role;
    private final String content;
    private final FunctionCall toolCall;

    public Message(Role role, String content, FunctionCall toolCall) {
        this.role = role;
        this.content = content;
        this.toolCall = toolCall;
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public FunctionCall getToolCall() {
        return toolCall;
    }
}
