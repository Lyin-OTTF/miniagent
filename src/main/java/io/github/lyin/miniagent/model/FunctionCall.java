package io.github.lyin.miniagent.model;

public class FunctionCall {
    private final String id;
    private final String name;
    private final String arguments;

    public FunctionCall(String id, String name, String arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public FunctionCall(String name, String arguments) {
        this(null, name, arguments);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getArguments() {
        return arguments;
    }
}
