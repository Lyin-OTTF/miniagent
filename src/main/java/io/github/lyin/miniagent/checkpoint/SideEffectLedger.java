package io.github.lyin.miniagent.checkpoint;

import io.github.lyin.miniagent.tool.ToolResult;

import java.util.HashMap;
import java.util.Map;

public class SideEffectLedger {

    // 账本：幂等键 → 工具执行结果
    private Map<String, ToolResult> entries = new HashMap<>();

    /**
     * 查账本：这个幂等键之前执行过吗？
     * 执行过就返回之前的结果，没执行过返回 null。
     */

    public ToolResult lookup(String idempotencyKey) {

        // 🔴 从 entries 里 get(idempotencyKey)，返回
        return entries.get(idempotencyKey);
        //return null; // 先留空
    }

    /**
     * 把执行结果记入账本。
     */
    public void record(String idempotencyKey, ToolResult result) {
        // 🔴 entries.put(idempotencyKey, result)
        entries.put(idempotencyKey, result);
    }

    public SideEffectLedger copy() {
        // 新建一个 SideEffectLedger
        SideEffectLedger copy = new SideEffectLedger();
        // 把 entries 这个 Map 的内容全部复制过去
        copy.entries = new HashMap<>(entries);
        // 提示：new HashMap<>(entries) 可以复制 Map
        return copy;
    }
}
