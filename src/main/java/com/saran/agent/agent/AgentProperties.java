package com.saran.agent.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(int maxIterations, String systemPrompt) {

    public AgentProperties {
        if (maxIterations <= 0) {
            maxIterations = 8;
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "You are a helpful assistant. Use the available tools when needed.";
        }
    }
}
