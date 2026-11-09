package io.github.lyin.miniagent.checkpoint;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 幂等键的生成规则。
 *
 * <p>「同一个工具 + 同一组参数」必须产出同一个键，否则续跑时账本查不到历史记录，去重直接失效。
 * 因此参数必须按 key 排序后拼接：{@code Map} 的迭代顺序不保证稳定，
 * 直接用 {@code toString()} 会因顺序不同而生成不同键。</p>
 */
public final class IdempotencyKey {

    private IdempotencyKey() {
    }

    public static String of(String toolName, Map<String, Object> args) {
        String sortedArgs = args.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
        return toolName + "|" + sortedArgs;
    }
}
