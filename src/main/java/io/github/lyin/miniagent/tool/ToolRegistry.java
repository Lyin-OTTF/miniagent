package io.github.lyin.miniagent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.log;

/**
 * 【需求说明，不用写进代码】
 * ToolRegistry：工具注册表
 * 作用：存放所有注册进来的Tool，提供按名称查找工具的能力
 * 核心约束（3条校验规则，必须在register里实现）
 * 1. 工具名称不能是null、空白、带空格
 * 2. 不允许同名工具重复注册
 * 3. 工具schema里标记为required的字段，必须在properties中存在定义
 */
public class ToolRegistry {

    // 🟢【可直接复制骨架】成员变量，有序Map保存工具
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    /**
     * 注册工具入口
     * @param tool 要注册的工具实例
     */
    public void register(Tool tool) {
        // ===================== 🔴【你手写区域开始】=====================
        // 步骤1：拿到工具名称 tool.name()
        String name = tool.name();
        // 步骤2：校验①：判断name是否为null、空白、包含空格，不满足则抛IllegalArgumentException
        if (name==null||name.isBlank()) {
            throw new IllegalArgumentException("工具名非法:" + name);
        }
        // 步骤3：校验②：判断tools这个map里是否已经存在这个name，存在抛异常（重名）
        if (tools.containsKey(name)){
            throw new IllegalArgumentException("工具名重复:" + name);
        }
        // 步骤4：拿到tool的schema：JsonSchema schema = tool.schema();
        JsonSchema schema = tool.schema();
        // 步骤5：校验③：循环schema.getRequired()里面的每一个字段名
        //        去schema.getProperties() 判断这个key是否存在
        //        不存在 → 抛异常：required引用了未定义的属性
        List<String> required =schema.getRequired();
        Map<String,?>propMap = schema.getProperties();
        for (String field : required){
            if (!propMap.containsKey(field))
            throw new IllegalArgumentException("required引用了未定义的属性:" + field);
        }

        // 步骤6：上面所有校验全部通过之后，才执行 put存入map
    tools.put(name,tool);
    // ===================== 🔴【你手写区域结束】=====================
    }

    /**
     * 根据工具名字获取工具
     * @param name 工具名
     * @return 匹配的Tool
     */
    public Tool get(String name) {
        // ===================== 🔴【你手写区域开始】=====================
        // 1. 根据name从tools中get拿到tool对象
        Tool tool = tools.get(name);
        // 2. 如果拿到是null，抛出异常：工具不存在
        if (tool == null){
            throw new IllegalArgumentException("工具不存在:" + name);
        }
        // 3. 存在就return返回tool
        return tool;
        // ===================== 🔴【你手写区域结束】=====================
    }

    // 🟢【可选拓展骨架，现在不用写，后面测试的时候再用】
    // public Collection<Tool> getAllTools() {
    //     return tools.values();
    // }
}