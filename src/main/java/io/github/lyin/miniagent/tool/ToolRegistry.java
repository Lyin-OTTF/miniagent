package io.github.lyin.miniagent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表：存放所有可用工具，并支持按名称查找。
 *
 * <p>校验放在注册期而非调用期，是因为非法工具属于开发者错误：
 * 让它在启动阶段就失败，比等到模型真的调用了才暴露要早得多，
 * 也避免一个坏工具瘫痪整轮对话。三条规则：名称非空且不含空格、不允许重名、
 * required 字段必须在 properties 中有定义。</p>
 */
public class ToolRegistry {

    /** 保持注册顺序，导出给模型时工具列表稳定，便于排查与缓存。 */
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    /**
     * 注册工具。任一条校验不通过即抛异常，且不留下半注册状态（校验全部通过后才写入）。
     */
    public void register(Tool tool) {
        String name = tool.name();
        if (name == null || name.isBlank() || name.contains(" ")) {
            throw new IllegalArgumentException("工具名非法: " + name);
        }
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("工具名重复: " + name);
        }

        JsonSchema schema = tool.schema();
        List<String> required = schema.getRequired();
        Map<String, ?> propertyMap = schema.getProperties();
        for (String field : required) {
            if (!propertyMap.containsKey(field)) {
                throw new IllegalArgumentException("required 引用了未定义的属性: " + field);
            }
        }

        tools.put(name, tool);
    }

    /**
     * 按名称取工具。
     *
     * @throws IllegalArgumentException 工具未注册
     */
    public Tool get(String name) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("工具不存在: " + name);
        }
        return tool;
    }

    /**
     * 按注册顺序返回全部工具，用于序列化给模型。
     *
     * <p>返回不可变副本：工具清单一旦交给调用方，注册表的后续变更不应影响它，
     * 否则同一轮对话里「问模型时用的工具清单」与「执行时查的工具表」可能不一致。</p>
     */
    public List<Tool> listTools() {
        return List.copyOf(tools.values());
    }
}
