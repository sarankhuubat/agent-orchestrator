package com.saran.agent.llm;

import com.saran.agent.llm.model.AnthropicModels.MessagesRequest;
import com.saran.agent.llm.model.AnthropicModels.MessagesResponse;

/**
 * Abstraction over the LLM provider so the agent loop can be unit-tested
 * with a fake and the provider can be swapped without touching the loop.
 */
public interface LlmClient {

    MessagesResponse send(MessagesRequest request);

    String defaultModel();

    int defaultMaxTokens();
}
