package io.github.lyin.miniagent.model;

public class Message {

    private final Role role;
    private final String content;
    private final FunctionCall toolCall;
    private final String toolCallId;

    public Message(Role role, String content, FunctionCall toolCall) {
        this(role, content, toolCall, null);
    }

    public Message(Role role, String content, FunctionCall toolCall, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCall = toolCall;
        this.toolCallId = toolCallId;
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

    public String getToolCallId() {
        return toolCallId;
    }
}
