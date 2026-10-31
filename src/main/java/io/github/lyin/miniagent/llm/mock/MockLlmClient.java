package io.github.lyin.miniagent.llm.mock;

import io.github.lyin.miniagent.llm.LlmClient;
import io.github.lyin.miniagent.model.ChatCompletionResponse;
import io.github.lyin.miniagent.model.Message;

import java.util.List;

import static io.github.lyin.miniagent.model.ChatCompletionResponse.answer;

public class MockLlmClient implements LlmClient {

    private final List<ChatCompletionResponse> script;

    private int turn = 0;

    public MockLlmClient (List<ChatCompletionResponse> script){
        this.script = script;
    }

    @Override
    public ChatCompletionResponse chat(List<Message> messages) {
        if (turn >= script.size()) {
            return answer("脚本结束");
        }
        ChatCompletionResponse resp = script.get(turn);
        turn++;
        return resp;

    }


    public int getTurn(){
        return turn;
    }
}
