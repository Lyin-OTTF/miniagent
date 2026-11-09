package io.github.lyin.miniagent.checkpoint;

import io.github.lyin.miniagent.tool.Tool;

import java.util.Map;

/**
 * Human-in-the-loop 闸门：在工具真正执行前插入一次审批。
 *
 * <p>审批策略做成可注入的函数式接口，而不是固定返回 true 的开关，
 * 原因是「哪些工具需要人工确认」属于业务决策（扣款要确认、查询不用），
 * 内核不应硬编码；同时便于测试中模拟拒绝与放行两条分支。</p>
 */
public class HitlGate {

    @FunctionalInterface
    public interface ApprovalPolicy {
        /**
         * @return true 放行执行；false 拒绝，工具不执行
         */
        boolean approve(Tool tool, Map<String, Object> args);
    }

    private final ApprovalPolicy policy;

    /** 默认全放行，等价于未启用 HITL。 */
    public HitlGate() {
        this((tool, args) -> true);
    }

    public HitlGate(ApprovalPolicy policy) {
        this.policy = policy;
    }

    public boolean approve(Tool tool, Map<String, Object> args) {
        return policy.approve(tool, args);
    }
}
