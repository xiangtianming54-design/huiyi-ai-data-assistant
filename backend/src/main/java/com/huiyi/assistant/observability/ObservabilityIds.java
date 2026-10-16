package com.huiyi.assistant.observability;

import java.util.UUID;

public final class ObservabilityIds {

    private ObservabilityIds() {
    }

    public static String requestId() {
        return "req_" + hexUuid();
    }

    public static String runId() {
        return "run_" + hexUuid();
    }

    public static boolean isValidRequestId(String value) {
        return value != null && !value.isBlank() && value.length() <= 64;
    }

    private static String hexUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
