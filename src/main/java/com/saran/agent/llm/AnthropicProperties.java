package com.saran.agent.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(
        String apiKey,
        String baseUrl,
        String model,
        int maxTokens,
        int timeoutSeconds
) {
    public AnthropicProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.anthropic.com";
        }
        if (maxTokens <= 0) {
            maxTokens = 1024;
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 30;
        }
    }
}
