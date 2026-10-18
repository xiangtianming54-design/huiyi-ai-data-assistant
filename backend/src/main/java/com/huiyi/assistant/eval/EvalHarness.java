package com.huiyi.assistant.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.assistant.agent.AgentDeadline;
import com.huiyi.assistant.agent.AgentRequest;
import com.huiyi.assistant.agent.AgentResult;
import com.huiyi.assistant.agent.AgentRunner;
import com.huiyi.assistant.agent.OutputGuard;
import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.context.WorkspaceContextHolder;

import java.util.List;

public class EvalHarness {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final OutputGuard OUTPUT_GUARD = new OutputGuard();

    public EvalActual run(EvalCase cse, AgentRunner runner) {
        try {
            WorkspaceContextHolder.set(contextFor(cse == null ? null : cse.workspace()));
            if (cse != null && cse.stubs() != null && cse.stubs().dirtyAnswer() != null) {
                String sanitized = OUTPUT_GUARD.sanitize(cse.stubs().dirtyAnswer());
                return new EvalActual(null, sanitized, null, false, List.of());
            }
            EvalRecordingListener listener = new EvalRecordingListener();
            AgentDeadline deadline = cse != null && cse.stubs() != null && cse.stubs().deadlineExpired()
                    ? AgentDeadline.alreadyExpired()
                    : AgentDeadline.fromNow(90_000L);
            String entrypoint = cse == null ? "analysis_query" : cse.entrypoint();
            String question = cse == null || cse.question() == null ? "" : cse.question();
            String sessionId = cse == null ? null : cse.sessionId();
            AgentResult result = runner.run(new AgentRequest(entrypoint, question, sessionId), listener, deadline);
            return toActual(result, listener);
        } finally {
            WorkspaceContextHolder.clear();
        }
    }

    public static WorkspaceContext contextFor(String workspace) {
        String label = workspace == null || workspace.isBlank() ? "default" : workspace;
        long workspaceId = switch (label) {
            case "default" -> 9L;
            case "A" -> 1L;
            case "B" -> 2L;
            default -> throw new IllegalArgumentException("unknown workspace label: " + workspace);
        };
        return new WorkspaceContext(1L, workspaceId, 1L, "VIEWER", "ACTIVE", "ACTIVE");
    }

    static EvalActual toActual(AgentResult result, EvalRecordingListener listener) {
        if (result == null) {
            return new EvalActual(null, "", null, false, listener == null ? List.of() : listener.events());
        }
        String intent = result.decision() == null || result.decision().intent() == null
                ? null
                : result.decision().intent().getCode();
        String answer = OUTPUT_GUARD.sanitize(result.answer());
        return new EvalActual(
                intent,
                answer,
                result.errorCode(),
                hasToolCalls(result),
                listener == null ? List.of() : listener.events());
    }

    private static boolean hasToolCalls(AgentResult result) {
        if (result.analysis() == null) {
            return false;
        }
        JsonNode node = MAPPER.valueToTree(result.analysis());
        return node != null && node.has("toolCalls");
    }
}
