package io.github.lyin.miniagent.loop;

import io.github.lyin.miniagent.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单次会话的可变执行状态：消息历史 + 轮次 + 终止原因 + 结构化结果。
 *
 * <p>它是唯一被写入快照的载体，但恢复语义只覆盖「历史 + 轮次」两项：
 * stopReason 与 structuredOutput 都是「本次运行得出、下次运行会重新得出」的结论，
 * 把它们带进续跑会误导调用方认为上次已经正常结束。循环体内的临时变量同样不参与持久化。</p>
 */
public class AgentState {

    private final List<Message> messages;
    private int turn;
    private String stopReason;
    private Map<String, Object> structuredOutput;

    public AgentState() {
        this.messages = new ArrayList<>();
        this.turn = 0;
        this.stopReason = null;
        this.structuredOutput = null;
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

    /** 保存校验通过后解析出的结构化答案，供调用方直接取字段而不必二次解析文本。 */
    public void setStructuredOutput(Map<String, Object> output) {
        this.structuredOutput = output;
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

    public Map<String, Object> getStructuredOutput() {
        return structuredOutput;
    }

    /**
     * 从快照重建状态。
     *
     * <p>只恢复历史与轮次、不恢复终止原因与结构化结果，是因为续跑意味着「上次没有正常结束」；
     * 若把上一次的结论带进来，会误导调用方认为任务已完成。</p>
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
