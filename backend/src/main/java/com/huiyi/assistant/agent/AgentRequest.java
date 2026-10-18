package com.huiyi.assistant.agent;

public record AgentRequest(String entrypoint, String question, String sessionId) {
}
