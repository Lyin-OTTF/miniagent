package io.github.lyin.miniagent.loop;

import io.github.lyin.miniagent.model.Message;

import java.util.ArrayList;
import java.util.List;

public class AgentState {
    private final List<Message> messages;
    private int turn;
    private String stopReason;

    //无参构造
    public AgentState(){
        this.messages=new ArrayList<>();
        this.turn=0;
        this.stopReason=null;
    }

    public void appendMessage(Message msg) {
        this.messages.add(msg);
    }
    //轮次 +1 的方法
    public void nextTurn() {
        turn++;
    }
    public void setTurn(int turn) {
        this.turn = turn;
    }

    //标记停止的方法
    public void stop(String reason) {
        this.stopReason = reason;
    }

    public List<Message> getMessages(){return messages;}
    public int getTurn(){return turn;}
    public String getStopReason(){return stopReason;}

    public static AgentState restore(List<Message> messages, int turn) {
        // 新建一个 AgentState
        // 把 messages 全部加进去
        // 设置 turn
        AgentState state = new AgentState();
        for (Message msg : messages) {
            state.appendMessage(msg);
        }
        state.setTurn(turn);
        return state;
    }
}
