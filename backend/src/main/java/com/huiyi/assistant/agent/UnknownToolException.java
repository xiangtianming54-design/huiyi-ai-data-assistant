package com.huiyi.assistant.agent;

public class UnknownToolException extends RuntimeException {

    private final String toolName;

    public UnknownToolException(String toolName) {
        super("unknown tool: " + toolName);
        this.toolName = toolName;
    }

    public String toolName() {
        return toolName;
    }
}
