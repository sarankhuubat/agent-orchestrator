package com.saran.agent.agent;

import com.saran.agent.llm.LlmClient;
import com.saran.agent.llm.model.AnthropicModels.ContentBlock;
import com.saran.agent.llm.model.AnthropicModels.Message;
import com.saran.agent.llm.model.AnthropicModels.MessagesRequest;
import com.saran.agent.llm.model.AnthropicModels.MessagesResponse;
import com.saran.agent.tools.Tool;
import com.saran.agent.tools.ToolExecutionException;
import com.saran.agent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The agent orchestration loop.
 *
 * <pre>
 * user message ─► Claude ─► stop_reason == "tool_use"? ──no──► final answer
 *                    ▲                │ yes
 *                    │                ▼
 *                    └──── tool_result ◄── execute tool(s)
 * </pre>
 *
 * The loop is bounded by {@code agent.max-iterations} to guarantee
 * termination even if the model keeps requesting tools.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final LlmClient llm;
    private final ToolRegistry toolRegistry;
    private final ConversationStore conversationStore;
    private final AgentProperties properties;

    public AgentService(LlmClient llm,
                        ToolRegistry toolRegistry,
                        ConversationStore conversationStore,
                        AgentProperties properties) {
        this.llm = llm;
        this.toolRegistry = toolRegistry;
        this.conversationStore = conversationStore;
        this.properties = properties;
    }

    /**
     * Run one agent turn: send the user message plus history, execute any
     * tools the model requests, and loop until the model produces a final
     * text answer (or the iteration budget is exhausted).
     */
    public AgentResult chat(String sessionId, String userMessage) {
        List<Message> working = new ArrayList<>(conversationStore.history(sessionId));
        List<Message> newMessages = new ArrayList<>();

        Message userMsg = Message.user(userMessage);
        working.add(userMsg);
        newMessages.add(userMsg);

        List<String> toolsUsed = new ArrayList<>();

        for (int iteration = 1; iteration <= properties.maxIterations(); iteration++) {
            MessagesResponse response = llm.send(new MessagesRequest(
                    llm.defaultModel(),
                    llm.defaultMaxTokens(),
                    properties.systemPrompt(),
                    List.copyOf(working),
                    toolRegistry.definitions()));

            Message assistantMsg = Message.assistant(response.content());
            working.add(assistantMsg);
            newMessages.add(assistantMsg);

            if (!response.wantsToolUse()) {  // stop_reason != "tool_use"
                conversationStore.append(sessionId, newMessages);
                return new AgentResult(response.joinedText(), toolsUsed, iteration);
            }

            Message toolResults = executeRequestedTools(response, toolsUsed);
            working.add(toolResults);
            newMessages.add(toolResults);
        }

        conversationStore.append(sessionId, newMessages);
        log.warn("Agent hit max iterations ({}) for session {}", properties.maxIterations(), sessionId);
        return new AgentResult(
                "I wasn't able to finish processing this request within my step budget. "
                        + "Please rephrase or break the request into smaller parts.",
                toolsUsed,
                properties.maxIterations());
    }

    private Message executeRequestedTools(MessagesResponse response, List<String> toolsUsed) {
        List<ContentBlock> results = new ArrayList<>();
        for (ContentBlock block : response.content()) {
            if (!block.hasTypeToolUse()) {
                continue;
            }
            toolsUsed.add(block.name());
            results.add(executeSingleTool(block));
        }
        return Message.toolResults(results);
    }

    private ContentBlock executeSingleTool(ContentBlock toolUse) {
        Optional<Tool> tool = toolRegistry.find(toolUse.name());
        if (tool.isEmpty()) {
            log.error("Model requested unknown tool: {}", toolUse.name());
            return ContentBlock.toolResult(toolUse.id(),
                    "Unknown tool: " + toolUse.name(), true);
        }
        Map<String, Object> input = toolUse.input() == null ? Map.of() : toolUse.input();
        try {
            String output = tool.get().execute(input);
            log.info("Tool {} executed ok", toolUse.name());
            return ContentBlock.toolResult(toolUse.id(), output, false);
        } catch (ToolExecutionException e) {
            log.info("Tool {} failed: {}", toolUse.name(), e.getMessage());
            return ContentBlock.toolResult(toolUse.id(), e.getMessage(), true);
        } catch (Exception e) {
            log.error("Tool {} threw unexpectedly", toolUse.name(), e);
            return ContentBlock.toolResult(toolUse.id(),
                    "Internal error executing tool", true);
        }
    }

    /** Outcome of one agent turn. */
    public record AgentResult(String reply, List<String> toolsUsed, int iterations) {
    }
}
