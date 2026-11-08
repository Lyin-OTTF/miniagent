package io.github.lyin.miniagent.checkpoint;

import io.github.lyin.miniagent.loop.AgentState;
import io.github.lyin.miniagent.model.Message;
import io.github.lyin.miniagent.model.Role;
import io.github.lyin.miniagent.tool.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointSnapshotTest {

    @Test
    void 快照后继续append_快照消息数不变() {
        // 造一个状态，加一条用户消息
        AgentState state = new AgentState();
        state.appendMessage(new Message(Role.USER, "你好", null));

        // 存快照
        Checkpoint cp = Checkpoint.from(state, new SideEffectLedger());

        // 原状态继续加消息
        state.appendMessage(new Message(Role.ASSISTANT, "你也好", null));

        // 快照里还是 1 条（没深拷贝就会变成 2）
        assertEquals(1, cp.getMessages().size());
    }

    @Test
    void 快照后原账本加key_快照账本看不到() {
        // 造一个账本，记一条
        SideEffectLedger ledger = new SideEffectLedger();
        ledger.record("key1", ToolResult.success("结果1"));

        // 造个空状态存快照
        AgentState state = new AgentState();
        Checkpoint cp = Checkpoint.from(state, ledger);

        // 原账本再加一条
        ledger.record("key2", ToolResult.success("结果2"));

        // 快照账本里应该看不到 key2（没拷贝就会看到）
        assertNull(cp.getLedger().lookup("key2"));
        // key1 还在
        assertNotNull(cp.getLedger().lookup("key1"));
    }
}
