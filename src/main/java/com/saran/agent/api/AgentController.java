package com.saran.agent.api;

import com.saran.agent.agent.AgentService;
import com.saran.agent.agent.AgentService.AgentResult;
import com.saran.agent.agent.ConversationStore;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final ConversationStore conversationStore;

    public AgentController(AgentService agentService, ConversationStore conversationStore) {
        this.agentService = agentService;
        this.conversationStore = conversationStore;
    }

    /**
     * One agent turn. If no sessionId is supplied a new session is created;
     * pass the returned sessionId on subsequent calls for multi-turn memory.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.sessionId();

        AgentResult result = agentService.chat(sessionId, request.message());
        return ResponseEntity.ok(new ChatResponse(
                sessionId, result.reply(), result.toolsUsed(), result.iterations()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> clearSession(@PathVariable String sessionId) {
        conversationStore.clear(sessionId);
        return ResponseEntity.noContent().build();
    }
}
