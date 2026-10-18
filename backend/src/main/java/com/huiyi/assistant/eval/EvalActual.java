package com.huiyi.assistant.eval;

import java.util.List;

public record EvalActual(
        String intent,
        String answer,
        String errorCode,
        boolean analysisHasToolCalls,
        List<ToolEvent> toolEvents
) {
    public EvalActual {
        answer = answer == null ? "" : answer;
        toolEvents = toolEvents == null ? List.of() : List.copyOf(toolEvents);
    }

    public record ToolEvent(String name, String phase, String status) {}
}
