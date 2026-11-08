package io.github.lyin.miniagent.checkpoint;

import io.github.lyin.miniagent.loop.AgentState;
import io.github.lyin.miniagent.model.Message;

import java.util.List;

public class Checkpoint {

    // 🔴 成员变量（4 个）
    // 1. List<Message> messages
    // 2. int turn
    // 3. String stopReason
    // 4. SideEffectLedger ledger
    private final List<Message> messages;
    private final int turn;
    private final String stopReason;
    private final SideEffectLedger ledger;

    // 🔴 构造器：接收 4 个参数，全 final
    public Checkpoint(List<Message> messages, int turn, String stopReason, SideEffectLedger ledger) {
        this.messages = messages;
        this.turn = turn;
        this.stopReason = stopReason;
        this.ledger = ledger;
    }

    // 🔴 静态工厂：from(AgentState state, SideEffectLedger ledger)
    //    从 AgentState 里取 messages、turn、stopReason
    public static Checkpoint from(AgentState state, SideEffectLedger ledger) {
        return new Checkpoint(state.getMessages(), state.getTurn(), state.getStopReason(), ledger);
    }

    // 🔴 getter：getMessages(), getTurn(), getStopReason(), getLedger()
    public List<Message> getMessages() {
        return messages;
    }
    public int getTurn() {
        return turn;
    }
    public String getStopReason() {
        return stopReason;
    }
    public SideEffectLedger getLedger() {
        return ledger;
    }
}
