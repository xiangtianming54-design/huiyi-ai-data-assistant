package com.huiyi.assistant.agent;

import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.context.WorkspaceContextHolder;
import com.huiyi.assistant.observability.ObservabilityContextHolder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
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
    void invalidSchemaDoesNotExecute() {
        AtomicBoolean ran = new AtomicBoolean(false);
        ToolRegistry registry = new ToolRegistry();
        registry.register(recordingToolWithSchema("sql.validate", ToolSchemas.SQL_VALIDATE, ran));
        ToolExecutor executor = new ToolExecutor(registry);
        ToolResult result = executor.execute("sql.validate", new ToolContext(1L, 1L, "q"), Map.of());
        assertFalse(result.ok());
        assertEquals("schema_invalid", result.error());
        assertFalse(ran.get());
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

    @Test
    void sequentialCallsUseSubmittingThreadContextNotPreviousPoolThread() {
        List<String> requestIds = new CopyOnWriteArrayList<>();
        List<String> runIds = new CopyOnWriteArrayList<>();
        List<Long> workspaceIds = new CopyOnWriteArrayList<>();
        ToolRegistry registry = new ToolRegistry();
        registry.register(recordingContextTool(requestIds, runIds, workspaceIds));
        ToolExecutor executor = new ToolExecutor(registry);
        try {
            ObservabilityContextHolder.setRequestId("req_aaa");
            ObservabilityContextHolder.setRunId("run_aaa");
            WorkspaceContextHolder.set(new WorkspaceContext(11L, 1L, 1L, "VIEWER", "ACTIVE", "ACTIVE"));
            executor.execute("ctx", new ToolContext(1L, 11L, "q"), Map.of());

            ObservabilityContextHolder.setRequestId("req_bbb");
            ObservabilityContextHolder.setRunId("run_bbb");
            WorkspaceContextHolder.set(new WorkspaceContext(22L, 9L, 2L, "VIEWER", "ACTIVE", "ACTIVE"));
            executor.execute("ctx", new ToolContext(9L, 22L, "q"), Map.of());
        } finally {
            ObservabilityContextHolder.clear();
            WorkspaceContextHolder.clear();
        }
        assertEquals(List.of("req_aaa", "req_bbb"), requestIds);
        assertEquals(List.of("run_aaa", "run_bbb"), runIds);
        assertEquals(List.of(1L, 9L), workspaceIds);
    }

    private static AgentTool recordingToolWithSchema(String name, String inputSchema, AtomicBoolean ran) {
        ToolSpec spec = new ToolSpec(name, "t", inputSchema, 5_000L, ToolSpec.DANGER_READ,
                ToolSpec.PERMISSION_WORKSPACE_MEMBER);
        return new AgentTool() {
            @Override
            public ToolSpec spec() {
                return spec;
            }

            @Override
            public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
                ran.set(true);
                return ToolResult.ok(Map.of());
            }
        };
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

    private static AgentTool recordingContextTool(List<String> requestIds, List<String> runIds,
                                                  List<Long> workspaceIds) {
        ToolSpec spec = new ToolSpec("ctx", "t", "{}", 5_000L, ToolSpec.DANGER_READ,
                ToolSpec.PERMISSION_WORKSPACE_MEMBER);
        return new AgentTool() {
            @Override
            public ToolSpec spec() {
                return spec;
            }

            @Override
            public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
                requestIds.add(ObservabilityContextHolder.requestId());
                runIds.add(ObservabilityContextHolder.runId());
                WorkspaceContext workspace = WorkspaceContextHolder.get();
                workspaceIds.add(workspace == null ? null : workspace.workspaceId());
                return ToolResult.ok(Map.of());
            }
        };
    }
}
