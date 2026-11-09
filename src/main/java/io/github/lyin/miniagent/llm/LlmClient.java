package io.github.lyin.miniagent.llm;

import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.Message;
import io.github.lyin.miniagent.tool.Tool;

import java.util.List;

public interface LlmClient {

    ChatCompletionResponse chat(List<Message> messages, List<Tool> tools);
}
