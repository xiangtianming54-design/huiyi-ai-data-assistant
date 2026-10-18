package com.huiyi.assistant.agent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {

    private final ConcurrentHashMap<String, AgentTool> tools = new ConcurrentHashMap<>();

    public void register(AgentTool tool) {
        String name = tool.spec().name();
        AgentTool previous = tools.putIfAbsent(name, tool);
        if (previous != null) {
            throw new IllegalStateException("duplicate tool: " + name);
        }
    }

    public AgentTool require(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new UnknownToolException(name);
        }
        return tool;
    }

    public boolean registered(String name) {
        return tools.containsKey(name);
    }

    public Set<String> names() {
        return Set.copyOf(tools.keySet());
    }
}
