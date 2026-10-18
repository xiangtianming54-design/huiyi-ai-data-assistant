package com.huiyi.assistant.eval;

import com.huiyi.assistant.agent.ToolEventListener;

import java.util.ArrayList;
import java.util.List;

public class EvalRecordingListener implements ToolEventListener {

    private final List<EvalActual.ToolEvent> events = new ArrayList<>();

    @Override
    public void onStarted(String toolName) {
        events.add(new EvalActual.ToolEvent(toolName, "started", null));
    }

    @Override
    public void onCompleted(String toolName, String status) {
        events.add(new EvalActual.ToolEvent(toolName, "completed", status));
    }

    @Override
    public void onFailed(String toolName, String status) {
        events.add(new EvalActual.ToolEvent(toolName, "failed", status));
    }

    public List<EvalActual.ToolEvent> events() {
        return List.copyOf(events);
    }
}
