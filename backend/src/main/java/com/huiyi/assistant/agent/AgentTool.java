package com.huiyi.assistant.agent;

import java.util.Map;

public interface AgentTool {

    ToolSpec spec();

    ToolResult execute(ToolContext context, Map<String, Object> arguments);
}
