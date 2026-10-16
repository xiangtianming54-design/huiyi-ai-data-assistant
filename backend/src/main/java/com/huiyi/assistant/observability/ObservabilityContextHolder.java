package com.huiyi.assistant.observability;

import org.slf4j.MDC;

public final class ObservabilityContextHolder {

    public static final String REQUEST_ID = "request_id";
    public static final String RUN_ID = "run_id";
    public static final String STEP_TYPE = "step_type";
    public static final String WORKSPACE_ID = "workspace_id";
    public static final String USER_ID = "user_id";

    private static final InheritableThreadLocal<String> REQUEST = new InheritableThreadLocal<>();
    private static final InheritableThreadLocal<String> RUN = new InheritableThreadLocal<>();
    private static final InheritableThreadLocal<String> STEP = new InheritableThreadLocal<>();

    private ObservabilityContextHolder() {
    }

    public static void setRequestId(String requestId) {
        REQUEST.set(requestId);
        putMdc(REQUEST_ID, requestId);
    }

    public static void setRunId(String runId) {
        RUN.set(runId);
        putMdc(RUN_ID, runId);
    }

    public static void setStepType(String stepType) {
        STEP.set(stepType);
        putMdc(STEP_TYPE, stepType);
    }

    public static void setWorkspace(Long workspaceId, Long userId) {
        putMdc(WORKSPACE_ID, workspaceId == null ? null : String.valueOf(workspaceId));
        putMdc(USER_ID, userId == null ? null : String.valueOf(userId));
    }

    public static String requestId() {
        return REQUEST.get();
    }

    public static String runId() {
        return RUN.get();
    }

    public static String stepType() {
        return STEP.get();
    }

    public static void clearStepType() {
        STEP.remove();
        MDC.remove(STEP_TYPE);
    }

    public static void clear() {
        REQUEST.remove();
        RUN.remove();
        STEP.remove();
        MDC.remove(REQUEST_ID);
        MDC.remove(RUN_ID);
        MDC.remove(STEP_TYPE);
        MDC.remove(WORKSPACE_ID);
        MDC.remove(USER_ID);
    }

    private static void putMdc(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
