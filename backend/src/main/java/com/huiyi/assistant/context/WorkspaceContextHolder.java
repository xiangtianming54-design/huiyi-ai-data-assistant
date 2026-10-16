package com.huiyi.assistant.context;

public final class WorkspaceContextHolder {

    private static final ThreadLocal<WorkspaceContext> HOLDER = new ThreadLocal<>();

    private WorkspaceContextHolder() {
    }

    public static void set(WorkspaceContext context) {
        HOLDER.set(context);
    }

    public static WorkspaceContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
