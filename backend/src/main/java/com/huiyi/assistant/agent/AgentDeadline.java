package com.huiyi.assistant.agent;

import java.util.concurrent.TimeUnit;

public final class AgentDeadline {

    private final long deadlineNano;

    private AgentDeadline(long deadlineNano) {
        this.deadlineNano = deadlineNano;
    }

    public static AgentDeadline fromNow(long timeoutMs) {
        long timeout = timeoutMs <= 0 ? 90_000L : timeoutMs;
        return new AgentDeadline(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout));
    }

    public static AgentDeadline alreadyExpired() {
        return new AgentDeadline(System.nanoTime() - 1);
    }

    public boolean expired() {
        return System.nanoTime() >= deadlineNano;
    }
}
