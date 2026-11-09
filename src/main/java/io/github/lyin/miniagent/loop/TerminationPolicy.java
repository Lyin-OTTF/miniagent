package io.github.lyin.miniagent.loop;

import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.FunctionCall;

/**
 * 终止条件判定：决定本轮之后「继续循环」还是「停下来」。
 *
 * <p>Agent 的死循环有三种成因，必须分别兜住：模型不停要调工具（轮次上限）、
 * 模型反复请求同一个工具同一组参数（重复检测）、模型主动说完了
 * （无工具调用，或调用了约定的 finish 工具）。只做轮次上限是不够的——
 * 那会让一次失控对话耗尽全部预算才停。</p>
 *
 * <p>注意：重复指纹使用原始 arguments 字符串而非解析后参数，
 * 因为判定发生在参数校验之前，此处不应假设 JSON 合法。</p>
 */
public class TerminationPolicy {

    public static final String REASON_FINISHED = "finished";
    public static final String REASON_MAX_TURNS = "max_turns";
    public static final String REASON_LOOP_DETECTED = "loop_detected";

    private final int maxTurns;
    private final int maxRepeat;

    private String lastKey = null;
    private int repeatCount = 0;

    public TerminationPolicy(int maxTurns, int maxRepeat) {
        this.maxTurns = maxTurns;
        this.maxRepeat = maxRepeat;
    }

    /**
     * @return 非 null 表示应停止，返回值即终止原因；null 表示继续循环
     */
    public String check(AgentState state, ChatCompletionResponse response) {
        if (state.getTurn() >= maxTurns) {
            return REASON_MAX_TURNS;
        }
        if (!response.hasToolCall()) {
            return REASON_FINISHED;
        }
        FunctionCall fc = response.getToolCall();
        if ("finish".equals(fc.getName())) {
            return REASON_FINISHED;
        }

        String key = fc.getName() + "|" + fc.getArguments();
        if (key.equals(lastKey)) {
            repeatCount++;
        } else {
            lastKey = key;
            repeatCount = 1;
        }
        if (repeatCount >= maxRepeat) {
            return REASON_LOOP_DETECTED;
        }
        return null;
    }
}
