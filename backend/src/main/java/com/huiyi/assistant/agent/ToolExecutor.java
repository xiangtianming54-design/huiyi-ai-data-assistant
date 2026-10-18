package com.huiyi.assistant.agent;

import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.context.WorkspaceContextHolder;
import com.huiyi.assistant.observability.ObservabilityContextHolder;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ToolExecutor {

    public static final String TIMEOUT_ERROR = "timeout";

    private final ToolRegistry registry;
    private final ExecutorService pool = Executors.newFixedThreadPool(8, runnable -> {
        Thread thread = new Thread(runnable, "huiyi-tool-exec");
        thread.setDaemon(true);
        return thread;
    });

    public ToolExecutor(ToolRegistry registry) {
        this.registry = registry;
    }

    public ToolResult execute(String name, ToolContext context, Map<String, Object> arguments) {
        AgentTool tool = registry.require(name);
        Map<String, Object> stripped = stripTenant(arguments);
        long timeoutMs = tool.spec().timeoutMs();
        if (timeoutMs <= 0) {
            return tool.execute(context, stripped);
        }
        WorkspaceContext workspace = WorkspaceContextHolder.get();
        String requestId = ObservabilityContextHolder.requestId();
        String runId = ObservabilityContextHolder.runId();
        String stepType = ObservabilityContextHolder.stepType();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Callable<ToolResult> task = () -> {
            try {
                restoreContext(workspace, requestId, runId, stepType, mdc);
                return tool.execute(context, stripped);
            } finally {
                WorkspaceContextHolder.clear();
                ObservabilityContextHolder.clear();
                MDC.clear();
            }
        };
        Future<ToolResult> future = pool.submit(task);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ToolResult.fail(TIMEOUT_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return ToolResult.fail(TIMEOUT_ERROR);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        }
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    private static void restoreContext(WorkspaceContext workspace, String requestId, String runId,
                                       String stepType, Map<String, String> mdc) {
        WorkspaceContextHolder.clear();
        ObservabilityContextHolder.clear();
        MDC.clear();
        if (workspace != null) {
            WorkspaceContextHolder.set(workspace);
        }
        if (requestId != null) {
            ObservabilityContextHolder.setRequestId(requestId);
        }
        if (runId != null) {
            ObservabilityContextHolder.setRunId(runId);
        }
        if (stepType != null) {
            ObservabilityContextHolder.setStepType(stepType);
        }
        if (mdc != null) {
            MDC.setContextMap(mdc);
        }
    }

    private static Map<String, Object> stripTenant(Map<String, Object> arguments) {
        Map<String, Object> copy = arguments == null ? new HashMap<>() : new HashMap<>(arguments);
        copy.remove("workspaceId");
        copy.remove("userId");
        return copy;
    }
}
