package io.github.lyin.miniagent.checkpoint;

import io.github.lyin.miniagent.tool.Tool;

import java.util.Map;

public class HitlGate {

    // 函数式接口：决定放不放行
    @FunctionalInterface
    public interface ApprovalPolicy {
        boolean approve(Tool tool, Map<String, Object> args);
    }

    private final ApprovalPolicy policy;

    // 默认构造：全放行
    public HitlGate() {
        this((t, a) -> true);
    }

    // 自定义策略
    public HitlGate(ApprovalPolicy policy) {
        this.policy = policy;
    }

    public boolean approve(Tool tool, Map<String, Object> args) {
        return policy.approve(tool, args);
    }
}
