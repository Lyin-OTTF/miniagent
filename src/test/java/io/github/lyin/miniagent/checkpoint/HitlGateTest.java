package io.github.lyin.miniagent.checkpoint;

import io.github.lyin.miniagent.tool.Tool;
import io.github.lyin.miniagent.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HitlGateTest {

    @Test
    void 默认放行_应该返回true() {
        HitlGate gate = new HitlGate();
        // 随便造个工具和参数，断言 approve 返回 true
        assertTrue(gate.approve(null, null));
    }

    @Test
    void 自定义拒绝_应该返回false() {
        //写一个匿名子类，覆盖 approve 方法返回 false
        HitlGate rejectGate = new HitlGate() {
            @Override
            public boolean approve(Tool tool, Map<String, Object> args) {
                return false;  // 强制拒绝
            }
        };

        //断言：approve 返回 false
        assertFalse(rejectGate.approve(null, null));
    }
}
