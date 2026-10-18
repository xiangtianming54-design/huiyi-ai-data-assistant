package com.huiyi.assistant.eval;

import java.util.LinkedHashMap;
import java.util.Map;

public record EvalReportRow(
        String id,
        boolean passed,
        Map<String, Object> expected,
        Map<String, Object> actual,
        String mismatch
) {
    public EvalReportRow {
        expected = expected == null ? Map.of() : new LinkedHashMap<>(expected);
        actual = actual == null ? Map.of() : new LinkedHashMap<>(actual);
    }
}
