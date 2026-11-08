package io.github.lyin.miniagent.checkpoint;

import io.github.lyin.miniagent.tool.Tool;

import java.util.Map;

public class HitlGate {
    /**
     * 写操作执行前，问人要不要放行。
     * 返回 true = 放行，false = 拒绝。
     *
     * 真实场景里这里会弹个框问用户；
     * 测试场景里我们直接返回 true（自动放行）。
     */
    public boolean approve(Tool tool, Map<String, Object> args) {
        // 真实场景：这里会暂停，等人点"同意"或"拒绝"
        // 现在先返回 true（自动放行），后面测试再改
        if (tool != null) {
            System.out.println("HitlGate: " + tool.name() + " " + args);
        }
        return true;
    }
}
