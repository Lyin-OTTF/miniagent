package io.github.lyin.miniagent.llm;

import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.Message;

import java.util.List;

/**
 * LLM 客户端抽象：MiniAgent 运行时与"具体用哪个模型"解耦。
 *
 * <p>两个实现长得一模一样，只是"内部如何产出结果"不同：</p>
 * <ul>
 *   <li>{@code mock.MockLlmClient}：不发网络请求，按预先写好的脚本逐轮返回，稳定可复现，供测试与演示；</li>
 *   <li>{@code OpenAiCompatibleClient}：按 OpenAI chat completions 线协议向 DeepSeek 等发起真实 HTTP 请求。</li>
 * </ul>
 *
 * <p>二者实现同一接口，Agent Loop 无需感知底层差异——这是"运行时与模型解耦"的落点。</p>
 */
public interface LlmClient {

    /**
     * 发起一次对话补全。
     *
     * @param messages 完整对话历史（按序包含 system / user / assistant / tool 各角色消息）
     * @return 模型响应：要么是最终文本答案（content），要么是一个工具调用（toolCall）
     */
    ChatCompletionResponse chat(List<Message> messages);
}
