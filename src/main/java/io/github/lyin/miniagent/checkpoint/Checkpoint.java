package io.github.lyin.miniagent.checkpoint;

import io.github.lyin.miniagent.loop.AgentState;
import io.github.lyin.miniagent.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次循环迭代结束后的不可变快照。
 *
 * <p>快照必须与执行中的状态完全脱钩，否则它会跟着后续 append 一起变化，
 * 续跑时读到的就不是中断那一刻的历史。故构造时对消息列表与副作用账本都做拷贝，
 * 且所有字段 final，杜绝快照被事后篡改。</p>
 */
public class Checkpoint {

    private final List<Message> messages;
    private final int turn;
    private final String stopReason;
    private final SideEffectLedger ledger;

    public Checkpoint(List<Message> messages, int turn, String stopReason, SideEffectLedger ledger) {
        this.messages = messages;
        this.turn = turn;
        this.stopReason = stopReason;
        this.ledger = ledger;
    }

    /**
     * 从当前状态与账本生成快照。两者都必须拷贝，否则存下来的只是活引用。
     */
    public static Checkpoint from(AgentState state, SideEffectLedger ledger) {
        return new Checkpoint(new ArrayList<>(state.getMessages()), state.getTurn(),
                state.getStopReason(), ledger.copy());
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

    public SideEffectLedger getLedger() {
        return ledger;
    }
}
