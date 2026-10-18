package com.huiyi.assistant.eval;

import java.util.Map;

public record EvalReportRow(
        String id,
        boolean passed,
        Map<String, Object> expected,
        Map<String, Object> actual,
        String mismatch
) {
    public EvalReportRow {
        expected = expected == null ? Map.of() : Map.copyOf(expected);
        actual = actual == null ? Map.of() : Map.copyOf(actual);
    }
}
