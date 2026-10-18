package com.huiyi.assistant.agent;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutorTest {

    @Test
    void stripsWorkspaceIdFromArguments() {
        AtomicReference<Map<String, Object>> received = new AtomicReference<>();
        ToolRegistry registry = new ToolRegistry();
        registry.register(recordingTool("echo", 5_000, received));
        ToolExecutor executor = new ToolExecutor(registry);
        executor.execute("echo", new ToolContext(2L, 9L, "q"),
                Map.of("workspaceId", 99L, "userId", 8L, "sql", "select 1"));
        Map<String, Object> args = received.get();
        assertFalse(args.containsKey("workspaceId"));
        assertFalse(args.containsKey("userId"));
        assertEquals("select 1", args.get("sql"));
    }

    @Test
    void requireUnknownPropagates() {
        ToolExecutor executor = new ToolExecutor(new ToolRegistry());
        UnknownToolException ex = assertThrows(UnknownToolException.class,
                () -> executor.execute("nope", new ToolContext(1L, 1L, "q"), Map.of()));
        assertEquals("nope", ex.toolName());
    }

    @Test
    void timeoutReturnsFail() {
        ToolRegistry registry = new ToolRegistry();
        ToolSpec spec = new ToolSpec("slow", "t", "{}", 50L, ToolSpec.DANGER_READ,
                ToolSpec.PERMISSION_WORKSPACE_MEMBER);
        registry.register(new AgentTool() {
            @Override
            public ToolSpec spec() {
                return spec;
            }

            @Override
            public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ToolResult.ok(Map.of());
            }
        });
        ToolExecutor executor = new ToolExecutor(registry);
        ToolResult result = executor.execute("slow", new ToolContext(1L, 1L, "q"), Map.of());
        assertFalse(result.ok());
        assertEquals("timeout", result.error());
    }

    private static AgentTool recordingTool(String name, long timeoutMs,
                                           AtomicReference<Map<String, Object>> received) {
        ToolSpec spec = new ToolSpec(name, "t", "{}", timeoutMs, ToolSpec.DANGER_READ,
                ToolSpec.PERMISSION_WORKSPACE_MEMBER);
        return new AgentTool() {
            @Override
            public ToolSpec spec() {
                return spec;
            }

            @Override
            public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
                received.set(new HashMap<>(arguments));
                return ToolResult.ok(Map.of());
            }
        };
    }
}
