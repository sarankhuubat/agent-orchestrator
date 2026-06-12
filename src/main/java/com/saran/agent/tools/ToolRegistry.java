package com.saran.agent.tools;

import com.saran.agent.llm.model.AnthropicModels.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Collects every {@link Tool} bean and exposes them as Anthropic tool
 * definitions plus a lookup for execution. Adding a new tool to the agent
 * is just "implement Tool, annotate with @Component".
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<Tool> discovered) {
        for (Tool tool : discovered) {
            Tool previous = tools.putIfAbsent(tool.name(), tool);
            if (previous != null) {
                throw new IllegalStateException("Duplicate tool name: " + tool.name());
            }
        }
    }

    public List<ToolDefinition> definitions() {
        return tools.values().stream()
                .map(t -> new ToolDefinition(t.name(), t.description(), t.inputSchema()))
                .toList();
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }
}
