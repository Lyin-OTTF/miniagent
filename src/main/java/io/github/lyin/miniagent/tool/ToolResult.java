package io.github.lyin.miniagent.tool;


public class ToolResult {

    //定义全局永久字段
        private final String text;
        private final boolean success;

        //构造器
        private ToolResult(String text, boolean success) {
            this.text = text;
            this.success = success;
        }

        //静态模板工厂，注意构造方法时要返回对应字段结果
    public static ToolResult success(String text) {
        return new ToolResult(text,true);
    }

    public static ToolResult failure(String text) {
        return new ToolResult(text,false);
    }

    //getter写法，注意返回值
    public boolean isSuccess(){
            return success;
    }
    public String getText(){
        return text;
    }


}
