package com.saran.agent.api;

import java.util.List;

public record ChatResponse(
        String sessionId,
        String reply,
        List<String> toolsUsed,
        int iterations
) {
}
