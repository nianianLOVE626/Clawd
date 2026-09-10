# Clawd Android 1.5 — Operit MCP Body

Clawd is the visual body for an AI running in Operit. It does not run a second chat brain.

## MCP

Default HTTP Streamable-style endpoint:

`http://127.0.0.1:18765/mcp`

Legacy SSE compatibility endpoint:

`http://127.0.0.1:18765/sse`

Health:

`http://127.0.0.1:18765/health`

The app shows and copies these addresses in its settings.

## Tools

- `clawd_say`
- `clawd_show_bubble`
- `clawd_set_mood`
- `clawd_set_action`
- `clawd_get_state`
- `clawd_voice`

The MCP server supports JSON-RPC `initialize`, `ping`, `tools/list`, and `tools/call`, bearer-token authentication, CORS preflight, MCP session headers, and legacy `/sse` + `/message` compatibility.

## Operit

Use the HTTP `/mcp` endpoint first. Prefer Operit's HTTP Streamable transport if available. If the build of Operit only exposes legacy SSE MCP, try `/sse`.

The server binds to `127.0.0.1` by default so it is not exposed to the LAN. If Operit cannot reach loopback from its execution environment, a future LAN mode can be enabled explicitly with authentication.
