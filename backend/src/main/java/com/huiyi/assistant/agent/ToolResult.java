package com.huiyi.assistant.agent;

import java.util.Map;

public record ToolResult(boolean ok, Map<String, Object> data, String error) {

    public static ToolResult ok(Map<String, Object> data) {
        return new ToolResult(true, data == null ? Map.of() : data, null);
    }

    public static ToolResult fail(String error) {
        return new ToolResult(false, Map.of(), error);
    }
}
