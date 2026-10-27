package io.github.lyin.miniagent.model;

//这里是要创建角色，创建4个，这4个负责收集消息的，然后返回给前端，供用户选择
public enum Role {
    SYSTEM("system"),  //每个枚举常量：大写名字 + (小写线上字符串)
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

//这是给每个角色背一个字符串字段，
private final String wire;

//这个是构造器，让上面的enum有返回的地方，有了这句才有上面enum的角色定义字段。（默认私有不用private）
    Role(String wire){
        this.wire=wire;
    }      //特意用this指针，把传进来的字符存一下，存成字段

//定义对外可读取的方法
    public String wire(){
        return wire;
    }

}
//总结就是，先创建角色，然后背上对应背上对应的final字符串字段，接着做可返回的构造器，最后一步就做可对外读取的方法
