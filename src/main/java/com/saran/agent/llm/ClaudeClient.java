package com.saran.agent.llm;

import com.saran.agent.llm.model.AnthropicModels.MessagesRequest;
import com.saran.agent.llm.model.AnthropicModels.MessagesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Thin, synchronous client for the Anthropic Messages API.
 * Kept deliberately small: one endpoint, explicit timeouts, no retries hidden
 * inside (retry policy belongs to the caller / a resilience layer).
 */
@Component
public class ClaudeClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final AnthropicProperties properties;

    public ClaudeClient(AnthropicProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("x-api-key", properties.apiKey())
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
    }

    @Override
    public MessagesResponse send(MessagesRequest request) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new LlmException("ANTHROPIC_API_KEY is not configured");
        }
        try {
            MessagesResponse response = restClient.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new LlmException("Anthropic API returned " + res.getStatusCode());
                    })
                    .body(MessagesResponse.class);
            if (response == null) {
                throw new LlmException("Empty response from Anthropic API");
            }
            if (response.usage() != null) {
                log.info("Claude call ok: model={} stopReason={} inputTokens={} outputTokens={}",
                        response.model(), response.stopReason(),
                        response.usage().inputTokens(), response.usage().outputTokens());
            }
            return response;
        } catch (RestClientException e) {
            throw new LlmException("Failed to call Anthropic API: " + e.getMessage(), e);
        }
    }

    @Override
    public String defaultModel() {
        return properties.model();
    }

    @Override
    public int defaultMaxTokens() {
        return properties.maxTokens();
    }
}
