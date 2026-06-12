package com.saran.agent.agent;

import com.saran.agent.llm.LlmClient;
import com.saran.agent.llm.model.AnthropicModels.ContentBlock;
import com.saran.agent.llm.model.AnthropicModels.MessagesRequest;
import com.saran.agent.llm.model.AnthropicModels.MessagesResponse;
import com.saran.agent.tools.ToolRegistry;
import com.saran.agent.tools.impl.CalculatorTool;
import com.saran.agent.tools.impl.OrderStatusTool;
import com.saran.agent.tools.impl.WeatherTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentServiceTest {

    private FakeLlmClient llm;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        llm = new FakeLlmClient();
        ToolRegistry registry = new ToolRegistry(
                List.of(new OrderStatusTool(), new CalculatorTool(), new WeatherTool()));
        agentService = new AgentService(
                llm, registry, new ConversationStore(),
                new AgentProperties(5, "test system prompt"));
    }

    @Test
    void returnsDirectAnswerWhenNoToolRequested() {
        llm.enqueue(textResponse("Hello! How can I help?"));

        AgentService.AgentResult result = agentService.chat("s1", "hi");

        assertThat(result.reply()).isEqualTo("Hello! How can I help?");
        assertThat(result.toolsUsed()).isEmpty();
        assertThat(result.iterations()).isEqualTo(1);
    }

    @Test
    void executesToolAndFeedsResultBack() {
        llm.enqueue(toolUseResponse("tu_1", "get_order_status", Map.of("order_id", "A-1001")));
        llm.enqueue(textResponse("Your order A-1001 has shipped via FedEx."));

        AgentService.AgentResult result = agentService.chat("s1", "Where is order A-1001?");

        assertThat(result.reply()).contains("A-1001 has shipped");
        assertThat(result.toolsUsed()).containsExactly("get_order_status");
        assertThat(result.iterations()).isEqualTo(2);

        // Second request must carry the tool_result back to the model
        MessagesRequest second = llm.requests.get(1);
        ContentBlock lastBlock = lastMessageFirstBlock(second);
        assertThat(lastBlock.type()).isEqualTo("tool_result");
        assertThat(lastBlock.toolUseId()).isEqualTo("tu_1");
        assertThat(lastBlock.content()).contains("FedEx");
        assertThat(lastBlock.isError()).isNull();
    }

    @Test
    void reportsToolFailureAsErrorResult() {
        llm.enqueue(toolUseResponse("tu_2", "get_order_status", Map.of("order_id", "ZZZ")));
        llm.enqueue(textResponse("I couldn't find that order."));

        AgentService.AgentResult result = agentService.chat("s1", "Where is order ZZZ?");

        assertThat(result.reply()).contains("couldn't find");
        ContentBlock lastBlock = lastMessageFirstBlock(llm.requests.get(1));
        assertThat(lastBlock.type()).isEqualTo("tool_result");
        assertThat(lastBlock.isError()).isTrue();
    }

    @Test
    void handlesUnknownToolGracefully() {
        llm.enqueue(toolUseResponse("tu_3", "nonexistent_tool", Map.of()));
        llm.enqueue(textResponse("Sorry, I can't do that."));

        AgentService.AgentResult result = agentService.chat("s1", "do something weird");

        ContentBlock lastBlock = lastMessageFirstBlock(llm.requests.get(1));
        assertThat(lastBlock.isError()).isTrue();
        assertThat(lastBlock.content()).contains("Unknown tool");
        assertThat(result.reply()).isEqualTo("Sorry, I can't do that.");
    }

    @Test
    void stopsAtMaxIterations() {
        for (int i = 0; i < 10; i++) {
            llm.enqueue(toolUseResponse("tu_" + i, "calculate",
                    Map.of("operation", "add", "a", 1, "b", 1)));
        }

        AgentService.AgentResult result = agentService.chat("s1", "loop forever");

        assertThat(result.iterations()).isEqualTo(5);
        assertThat(llm.requests).hasSize(5);
        assertThat(result.reply()).contains("step budget");
    }

    @Test
    void maintainsConversationHistoryAcrossTurns() {
        llm.enqueue(textResponse("First answer"));
        llm.enqueue(textResponse("Second answer"));

        agentService.chat("s1", "first question");
        agentService.chat("s1", "second question");

        // Second request must include: user1, assistant1, user2
        MessagesRequest second = llm.requests.get(1);
        assertThat(second.messages()).hasSize(3);
        assertThat(second.messages().get(0).role()).isEqualTo("user");
        assertThat(second.messages().get(1).role()).isEqualTo("assistant");
        assertThat(second.messages().get(2).role()).isEqualTo("user");
    }

    // ---------- helpers ----------

    private static MessagesResponse textResponse(String text) {
        return new MessagesResponse("msg_test", "test-model", "assistant",
                List.of(ContentBlock.text(text)), "end_turn", null);
    }

    private static MessagesResponse toolUseResponse(String id, String toolName, Map<String, Object> input) {
        ContentBlock toolUse = new ContentBlock(
                "tool_use", null, id, toolName, input, null, null, null);
        return new MessagesResponse("msg_test", "test-model", "assistant",
                List.of(toolUse), "tool_use", null);
    }

    private static ContentBlock lastMessageFirstBlock(MessagesRequest request) {
        List<ContentBlock> content = request.messages()
                .get(request.messages().size() - 1)
                .content();
        return content.get(0);
    }

    /** Scripted fake: returns queued responses and records every request. */
    private static final class FakeLlmClient implements LlmClient {

        private final Deque<MessagesResponse> scripted = new ArrayDeque<>();
        private final List<MessagesRequest> requests = new ArrayList<>();

        void enqueue(MessagesResponse response) {
            scripted.add(response);
        }

        @Override
        public MessagesResponse send(MessagesRequest request) {
            requests.add(request);
            MessagesResponse next = scripted.poll();
            if (next == null) {
                throw new IllegalStateException("No scripted response left");
            }
            return next;
        }

        @Override
        public String defaultModel() {
            return "test-model";
        }

        @Override
        public int defaultMaxTokens() {
            return 256;
        }
    }
}
