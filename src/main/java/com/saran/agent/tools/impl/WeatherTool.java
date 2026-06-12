package com.saran.agent.tools.impl;

import com.saran.agent.tools.Tool;
import com.saran.agent.tools.ToolExecutionException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Mock weather provider used for delivery-delay estimates.
 * Swap the body for a real weather API call without changing the contract.
 */
@Component
public class WeatherTool implements Tool {

    private static final Map<String, String> FORECASTS = Map.of(
            "austin", "Sunny, 95F, no delivery impact expected",
            "seattle", "Heavy rain, minor carrier delays possible",
            "chicago", "Thunderstorms, 1-2 day carrier delays likely",
            "new york", "Partly cloudy, 78F, no delivery impact expected"
    );

    @Override
    public String name() {
        return "get_weather";
    }

    @Override
    public String description() {
        return "Get the current weather forecast for a US city, including any "
                + "expected impact on package delivery times.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of(
                                "type", "string",
                                "description", "City name, e.g. Austin")),
                "required", new String[]{"city"});
    }

    @Override
    public String execute(Map<String, Object> input) {
        Object raw = input.get("city");
        if (raw == null) {
            throw new ToolExecutionException("city is required");
        }
        String city = raw.toString().trim().toLowerCase(Locale.US);
        String forecast = FORECASTS.get(city);
        if (forecast == null) {
            throw new ToolExecutionException("No forecast available for " + raw);
        }
        return "Weather in " + raw + ": " + forecast;
    }
}
