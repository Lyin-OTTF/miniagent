package io.github.lyin.miniagent.loop;

import io.github.lyin.miniagent.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次会话的可变执行状态：消息历史 + 轮次 + 终止原因。
 *
 * <p>它是唯一被写入快照的载体，因此恢复语义只需覆盖这三个字段，
 * 循环体内的临时变量一律不参与持久化。</p>
 */
public class AgentState {

    private final List<Message> messages;
    private int turn;
    private String stopReason;

    public AgentState() {
        this.messages = new ArrayList<>();
        this.turn = 0;
        this.stopReason = null;
    }

    public void appendMessage(Message msg) {
        this.messages.add(msg);
    }

    public void nextTurn() {
        turn++;
    }

    public void setTurn(int turn) {
        this.turn = turn;
    }

    public void stop(String reason) {
        this.stopReason = reason;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public int getTurn() {
        return turn;
    }

    public String getStopReason() {
        return stopReason;
    }

    /**
     * 从快照重建状态。
     *
     * <p>只恢复历史与轮次、不恢复 stopReason，是因为续跑意味着「上次没有正常结束」；
     * 若把上一次的终止原因带进来，会误导调用方认为任务已完成。</p>
     */
    public static AgentState restore(List<Message> messages, int turn) {
        AgentState state = new AgentState();
        for (Message msg : messages) {
            state.appendMessage(msg);
        }
        state.setTurn(turn);
        return state;
    }
}
