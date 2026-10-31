package io.github.lyin.miniagent.tool;

import java.util.Map;

public interface Tool {
    //起名字区分工具
    String name();
    //说明工具的用途
    String description();
    //传什么参数
    JsonSchema schema();
    //执行工具口
    ToolResult call(Map<String,Object>args);
}
