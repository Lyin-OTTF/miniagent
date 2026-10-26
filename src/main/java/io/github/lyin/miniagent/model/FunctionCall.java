package io.github.lyin.miniagent.model;

public class FunctionCall {//定义最简单的"数据袋子"
    private final String name;
    private final String arguments;   //存原始字段

    //写构造器
    public FunctionCall(String name,String arguments){
        this.name=name;
        this.arguments=arguments;
    }

    //写两个getter
    public String getName (){
        return name;
    }
    public String getArguments(){
        return arguments;
    }


}
