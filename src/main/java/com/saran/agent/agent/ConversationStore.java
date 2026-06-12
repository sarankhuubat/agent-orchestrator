package com.saran.agent.agent;

import com.saran.agent.llm.model.AnthropicModels.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-session conversation history.
 * Bounded to the most recent messages to keep prompts within budget.
 * Swap for Redis/Postgres for multi-instance deployments.
 */
@Component
public class ConversationStore {

    private static final int MAX_MESSAGES = 40;

    private final Map<String, List<Message>> conversations = new ConcurrentHashMap<>();

    public List<Message> history(String sessionId) {
        return List.copyOf(conversations.getOrDefault(sessionId, List.of()));
    }

    public void append(String sessionId, List<Message> newMessages) {
        conversations.compute(sessionId, (id, existing) -> {
            List<Message> updated = existing == null
                    ? new ArrayList<>()
                    : new ArrayList<>(existing);
            updated.addAll(newMessages);
            if (updated.size() > MAX_MESSAGES) {
                updated = new ArrayList<>(updated.subList(updated.size() - MAX_MESSAGES, updated.size()));
            }
            return updated;
        });
    }

    public void clear(String sessionId) {
        conversations.remove(sessionId);
    }
}
