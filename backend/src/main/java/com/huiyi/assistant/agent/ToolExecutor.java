package com.huiyi.assistant.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ToolExecutor {

    private final ToolRegistry registry;
    private final ExecutorService pool = Executors.newCachedThreadPool(runnable -> {
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
        Future<ToolResult> future = pool.submit(() -> tool.execute(context, stripped));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ToolResult.fail("timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return ToolResult.fail("timeout");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static Map<String, Object> stripTenant(Map<String, Object> arguments) {
        Map<String, Object> copy = arguments == null ? new HashMap<>() : new HashMap<>(arguments);
        copy.remove("workspaceId");
        copy.remove("userId");
        return copy;
    }
}
