package com.saran.agent.tools;

import java.util.Map;

/**
 * A capability the agent can invoke. Implementations are discovered by
 * Spring and registered automatically in {@link ToolRegistry}.
 */
public interface Tool {

    /** Unique tool name advertised to the model. */
    String name();

    /** Natural-language description the model uses to decide when to call this tool. */
    String description();

    /** JSON Schema (as a Map) describing the expected input. */
    Map<String, Object> inputSchema();

    /**
     * Execute the tool.
     *
     * @param input arguments produced by the model, already parsed from JSON
     * @return human/model-readable result string
     * @throws ToolExecutionException when the tool fails in an expected way
     */
    String execute(Map<String, Object> input);
}
