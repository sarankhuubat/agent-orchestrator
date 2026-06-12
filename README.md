# agent-orchestrator

Small Spring Boot service that runs an LLM agent loop. You send a message to a REST
endpoint, Claude decides which backend tools it needs (order lookup, weather,
calculator), the service executes them and feeds the results back, and this repeats
until the model has a final answer.

I built this to dig into how agent orchestration actually works under the hood
(tool use, the request/response loop, error feedback) instead of relying on a
framework to hide it.

```
POST /api/agent/chat
      |
      v
 AgentService loop:
   1. send history + tool defs to Claude
   2. stop_reason == "tool_use"?
        no  -> return answer
        yes -> run the requested tool(s), append tool_result, goto 1
   (capped at agent.max-iterations so it can't spin forever)
```

## Running it

Needs Java 17 and Maven.

```bash
export ANTHROPIC_API_KEY=sk-ant-...
mvn spring-boot:run
```

```bash
curl -s localhost:8080/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message": "Where is order A-1001, and will the weather in Austin delay it?"}'
```

```json
{
  "sessionId": "1c2f...",
  "reply": "Order A-1001 shipped via FedEx... Austin is sunny, no delays expected.",
  "toolsUsed": ["get_order_status", "get_weather"],
  "iterations": 2
}
```

Send the `sessionId` back in the next request to continue the conversation.
`DELETE /api/agent/sessions/{id}` clears it.

Tests don't need an API key:

```bash
mvn test
```

## How it's put together

- `agent/AgentService` - the loop itself. Bounded iterations, executes tools,
  reports failures back to the model as `is_error` tool results so it can tell
  the user what went wrong instead of making something up.
- `llm/ClaudeClient` - small synchronous client for the Anthropic Messages API.
  The loop only sees the `LlmClient` interface, which is what makes the tests
  possible (there's a scripted fake in the test sources).
- `tools/` - `Tool` interface + registry. New tool = implement the interface,
  add `@Component`, done. The registry picks it up at startup and advertises it
  to the model.
- `agent/ConversationStore` - in-memory per-session history with a bounded
  window. Fine for a single instance; would be Redis/Postgres in real life.

The three demo tools return canned data on purpose. The point is the
orchestration, not the integrations - swap the tool bodies for real calls and
nothing else changes.

## Config

Everything lives in `application.yml`. The ones you might touch:

| key | default | |
|---|---|---|
| `anthropic.model` | `claude-sonnet-4-6` | model id |
| `agent.max-iterations` | 8 | loop budget per turn |
| `agent.system-prompt` | support-agent persona | what the agent acts like |

## Things I'd add next

- streaming responses (SSE)
- retry/circuit breaker around the API client (Resilience4j)
- parallel tool execution when the model requests several at once
