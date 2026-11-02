package io.github.lyin.miniagent.loop;

import io.github.lyin.miniagent.model.ChatCompletionResponse;

public class TerminationPolicy {
    // 最大轮次（构造器传进来，比如 10）
    private final int maxTurns;

    // 死循环检测：连续重复几次就停（比如 3）
    private final int maxRepeat;

    // 构造器
    public TerminationPolicy(int maxTurns, int maxRepeat) {
        this.maxTurns = maxTurns;
        this.maxRepeat = maxRepeat;
    }

    public String check(AgentState state, ChatCompletionResponse response){
        //轮次超限
        if (state.getTurn() >= maxTurns)
        return "max_turns";
        //模型没有工具调用
        if (response.hasToolCall() == false)
            return "finished";
        //模型调了名为 "finish" 的工具
        if (response.hasToolCall() && response.getToolCall().getName().equals("finish"))
            return "finished";
        //死循环检测：这个我们先留空，阶段三最后再补
        //都没触发 → return null（继续循环）
        return null;
    }
}
