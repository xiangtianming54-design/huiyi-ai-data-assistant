package com.huiyi.assistant.agent;

public record ToolSpec(String name, String description, String inputSchema, long timeoutMs,
                       String dangerLevel, String permissions) {

    public static final String DANGER_READ = "read";
    public static final String PERMISSION_WORKSPACE_MEMBER = "workspace_member";
}
