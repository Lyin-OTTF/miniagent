package io.github.lyin.miniagent.checkpoint;

import io.github.lyin.miniagent.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SideEffectLedgerTest {

    @Test
    void 同一工具同一参数_第二次不重复执行() {
        // 造一个计数器，记录工具真实执行次数
        AtomicInteger callCount = new AtomicInteger(0);

        // 造账本
        SideEffectLedger ledger = new SideEffectLedger();

        // 第一次调用：算幂等键，查账本，没命中就执行，记入账本
        String key = IdempotencyKey.of("query_order", Map.of("orderId", "123"));
        ToolResult result = ledger.lookup(key);
        if (result == null) {
            callCount.incrementAndGet();  // 真实执行
            result = ToolResult.success("订单123");
            ledger.record(key, result);
        }

        // 第二次调用：同样的 key，查账本，命中就不执行
        result = ledger.lookup(key);
        if (result == null) {
            callCount.incrementAndGet();  // 这行不应该执行
            result = ToolResult.success("订单123");
            ledger.record(key, result);
        }

        //断言：callCount == 1（只执行了一次）
        assertEquals(1, callCount.get());
    }
}
