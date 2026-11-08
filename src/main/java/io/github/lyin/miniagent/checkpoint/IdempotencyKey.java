package io.github.lyin.miniagent.checkpoint;

import java.util.Map;
import java.util.stream.Collectors;

public class IdempotencyKey {

    /**
     * 根据工具名 + 参数 Map，生成一个稳定的幂等键。
     * 同一个工具 + 同一组参数，每次生成的 key 必须一样。
     */
    public static String of(String toolName, Map<String, Object> args) {
        // 🔴 思路：
        // 1. 先把 args 的 key 排序（因为 Map 顺序不固定，不排序就会生成不同的 key）
        String sortedArgs = args.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
        // 2. 拼成 "工具名|key1=value1;key2=value2" 这样的字符串
        String key = toolName + "|" + sortedArgs;
        // 3. 返回这个字符串作为幂等键
        return key;

        // 简单实现：
        // return toolName + "|" + args.toString();
        // 但 args.toString() 顺序不固定，所以要先排序
        //return null; // 先留空
    }
}
