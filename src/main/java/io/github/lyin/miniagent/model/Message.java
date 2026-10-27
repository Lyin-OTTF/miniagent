package io.github.lyin.miniagent.model;

public class Message {
    //字段+构造器
    private final Role role;
    private final String content;
    private final FunctionCall toolCall;

    public Message(Role role,String content,FunctionCall toolCall){
        this.role = role;
        this.content = content;
        this.toolCall = toolCall;
    }


    public Role getRole(){return role;}
    public String getContent(){return content;}
    public FunctionCall getToolCall(){return toolCall;}


}
