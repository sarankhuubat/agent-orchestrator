package com.saran.agent.llm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Wire-format records for the Anthropic Messages API
 * (https://docs.claude.com/en/api/messages), including tool use.
 */
public final class AnthropicModels {

    private AnthropicModels() {
    }

    /** A single message in the conversation: role is "user" or "assistant". */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(String role, List<ContentBlock> content) {

        public static Message user(String text) {
            return new Message("user", List.of(ContentBlock.text(text)));
        }

        public static Message assistant(List<ContentBlock> content) {
            return new Message("assistant", content);
        }

        public static Message toolResults(List<ContentBlock> results) {
            return new Message("user", results);
        }
    }

    /**
     * One content block. Covers the three block types the agent loop needs:
     * "text", "tool_use" (model wants a tool executed) and
     * "tool_result" (we report the tool output back).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(
            String type,
            String text,
            String id,
            String name,
            Map<String, Object> input,
            @JsonProperty("tool_use_id") String toolUseId,
            String content,
            @JsonProperty("is_error") Boolean isError
    ) {

        public static ContentBlock text(String text) {
            return new ContentBlock("text", text, null, null, null, null, null, null);
        }

        public static ContentBlock toolResult(String toolUseId, String result, boolean isError) {
            return new ContentBlock("tool_result", null, null, null, null,
                    toolUseId, result, isError ? Boolean.TRUE : null);
        }

        public boolean hasTypeText() {
            return "text".equals(type);
        }

        public boolean hasTypeToolUse() {
            return "tool_use".equals(type);
        }
    }

    /** Tool definition advertised to the model. */
    public record ToolDefinition(
            String name,
            String description,
            @JsonProperty("input_schema") Map<String, Object> inputSchema
    ) {
    }

    /** Request body for POST /v1/messages. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MessagesRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<Message> messages,
            List<ToolDefinition> tools
    ) {
    }

    /** Response body from POST /v1/messages. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessagesResponse(
            String id,
            String model,
            String role,
            List<ContentBlock> content,
            @JsonProperty("stop_reason") String stopReason,
            Usage usage
    ) {

        public boolean wantsToolUse() {
            return "tool_use".equals(stopReason);
        }

        public String joinedText() {
            if (content == null) {
                return "";
            }
            return content.stream()
                    .filter(ContentBlock::hasTypeText)
                    .map(ContentBlock::text)
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens
    ) {
    }
}
