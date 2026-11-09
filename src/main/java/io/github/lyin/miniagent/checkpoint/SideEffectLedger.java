package io.github.lyin.miniagent.checkpoint;

import io.github.lyin.miniagent.tool.ToolResult;

import java.util.HashMap;
import java.util.Map;

/**
 * 副作用账本：记录「幂等键 → 工具执行结果」。
 *
 * <p>它解决的是续跑语义问题：进程在工具已产生副作用后崩溃，恢复时模型会再次请求同一个工具；
 * 若直接执行就会重复扣款、重复扣库存。账本命中即回填历史结果、跳过执行，
 * 使「已完成并记账的步骤」不重放。</p>
 *
 * <p>注意语义边界：账本只覆盖「已记账」的步骤。若崩溃恰好落在
 * 「工具已执行、账本尚未写入」之间，续跑仍可能重放一次，即 at-least-once；
 * 要做到 exactly-once 需配合幂等写或两阶段提交。</p>
 */
public class SideEffectLedger {

    private final Map<String, ToolResult> entries;

    public SideEffectLedger() {
        this(new HashMap<>());
    }

    private SideEffectLedger(Map<String, ToolResult> entries) {
        this.entries = entries;
    }

    /**
     * 查询该幂等键是否已执行过。
     *
     * @return 执行过则返回当时的结果，否则返回 {@code null}
     */
    public ToolResult lookup(String idempotencyKey) {
        return entries.get(idempotencyKey);
    }

    /**
     * 工具执行成功后把结果记入账本。必须在副作用生效后立刻调用，
     * 记账窗口越长，续跑重放的窗口越大。
     */
    public void record(String idempotencyKey, ToolResult result) {
        entries.put(idempotencyKey, result);
    }

    /**
     * 深拷贝。快照必须持有独立副本，否则原账本后续新增的 key 会「泄漏」进已生成的快照。
     */
    public SideEffectLedger copy() {
        return new SideEffectLedger(new HashMap<>(entries));
    }
}
