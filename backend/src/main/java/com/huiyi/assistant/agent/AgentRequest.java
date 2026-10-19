package com.huiyi.assistant.agent;

import java.util.List;

public record AgentRequest(String entrypoint, String question, String sessionId, List<Long> spaceIds) {

    public AgentRequest(String entrypoint, String question, String sessionId) {
        this(entrypoint, question, sessionId, null);
    }
}
