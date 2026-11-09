package io.github.lyin.miniagent;

import io.github.lyin.miniagent.llm.LlmClient;
import io.github.lyin.miniagent.llm.OpenAiCompatibleClient;
import io.github.lyin.miniagent.loop.AgentState;
import io.github.lyin.miniagent.model.FunctionCall;
import io.github.lyin.miniagent.model.Message;
import io.github.lyin.miniagent.tool.JsonSchema;
import io.github.lyin.miniagent.tool.Tool;
import io.github.lyin.miniagent.tool.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 真实模型联调入口：用 DeepSeek（或任何 OpenAI 兼容服务）跑一轮工具调用。
 *
 * <p>存在的理由：{@link Main} 全程用 Mock 剧本，能证明循环与账本逻辑正确，
 * 但证明不了「真实上游会接受我们发出的请求体」。协议字段是否合规只能由真实服务裁决，
 * 所以这里把 {@link LlmClient} 换成真实实现跑一遍，作为「真实模型多轮工具调用跑通」的复现入口。</p>
 *
 * <p>凭据只从环境变量读，不接受命令行参数，避免 Key 落进 shell 历史与截图。</p>
 */
public class RealApiDemo {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-chat";

    public static void main(String[] args) {
        String apiKey = firstNonBlank(System.getenv("DEEPSEEK_API_KEY"), System.getenv("OPENAI_API_KEY"));
        if (apiKey == null) {
            System.out.println("未找到 API Key，跳过真实联调。");
            System.out.println("用法:");
            System.out.println("  export DEEPSEEK_API_KEY=sk-xxxx        # 必需");
            System.out.println("  export DEEPSEEK_BASE_URL=...            # 可选，默认 " + DEFAULT_BASE_URL);
            System.out.println("  export DEEPSEEK_MODEL=...               # 可选，默认 " + DEFAULT_MODEL);
            System.exit(2);
            return;
        }

        String baseUrl = firstNonBlank(System.getenv("DEEPSEEK_BASE_URL"), DEFAULT_BASE_URL);
        String model = firstNonBlank(System.getenv("DEEPSEEK_MODEL"), DEFAULT_MODEL);
        LlmClient llm = new OpenAiCompatibleClient(baseUrl, apiKey, model);

        AtomicInteger queryCount = new AtomicInteger();
        Agent agent = new Agent(llm, "real-api-smoke");
        agent.registerTool(queryOrderTool(queryCount));

        System.out.println("=== 真实模型联调 ===");
        System.out.println("baseUrl : " + baseUrl);
        System.out.println("model   : " + model);
        System.out.println("apiKey  : " + mask(apiKey));
        System.out.println();

        AgentState state;
        try {
            state = agent.run("请查询订单 A1001 的状态。必须先调用 query_order 工具，再根据工具结果回答。");
        } catch (RuntimeException e) {
            // 真实链路最常见的失败是鉴权与模型名，原样打印上游报文便于定位
            System.out.println("调用失败: " + e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("终止原因: " + state.getStopReason());
        System.out.println("工具实际执行次数: query_order=" + queryCount.get());
        System.out.println("消息历史:");
        for (Message msg : state.getMessages()) {
            System.out.println("  [" + msg.getRole().wire() + "] " + describe(msg));
        }

        // 真实模型不保证一定调用工具：不调就说明提示词或 schema 描述需要调整，如实标出而非假装通过
        if (queryCount.get() == 0) {
            System.out.println();
            System.out.println("注意: 模型本轮没有调用任何工具，多轮工具调用链路未被验证。");
        }
    }

    /** 演示用工具：返回固定订单数据，只为让模型有真实可用的工具可调。 */
    private static Tool queryOrderTool(AtomicInteger counter) {
        JsonSchema schema = JsonSchema.builder()
                .addProperty("orderId", "string", "订单号")
                .required("orderId")
                .build();
        return new Tool() {
            @Override public String name() { return "query_order"; }
            @Override public String description() { return "按订单号查询订单状态"; }
            @Override public JsonSchema schema() { return schema; }

            @Override
            public ToolResult call(Map<String, Object> args) {
                counter.incrementAndGet();
                String orderId = String.valueOf(args.get("orderId"));
                return ToolResult.success("订单 " + orderId + " 状态：已发货，承运商：顺丰，运单号 SF1234567890");
            }
        };
    }

    private static String describe(Message msg) {
        FunctionCall fc = msg.getToolCall();
        if (fc != null) {
            return "[请求工具 " + fc.getName() + " 参数=" + fc.getArguments() + " id=" + fc.getId() + "]";
        }
        return msg.getContent();
    }

    private static String mask(String key) {
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 6) + "****" + key.substring(key.length() - 4);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
