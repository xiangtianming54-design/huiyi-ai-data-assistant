package com.huiyi.assistant.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalComparerTest {

    @Test
    void schemaInvalidNotLimitCode() {
        EvalComparer comparer = new EvalComparer();
        EvalCase.Expect expect = new EvalCase.Expect();
        expect.setIntent("data_query");
        expect.setAnswerEquals("工具调用失败，请稍后重试。");
        expect.setErrorCode(null);
        EvalActual ok = new EvalActual("data_query", "工具调用失败，请稍后重试。", null, false, List.of());
        assertTrue(comparer.compare(expect, ok).passed());
        EvalActual limit = new EvalActual("data_query", "工具调用失败，请稍后重试。", "AGENT_LIMIT_EXCEEDED", false, List.of());
        EvalComparer.Result failed = comparer.compare(expect, limit);
        assertFalse(failed.passed());
        assertEquals("errorCode", failed.mismatch());
    }

    @Test
    void emptyToolEventsMustBeExact() {
        EvalComparer comparer = new EvalComparer();
        EvalCase.Expect expect = new EvalCase.Expect();
        expect.setIntent("data_query");
        expect.setAnswerEquals("该请求涉及数据修改操作，已被安全策略拒绝。系统仅支持只读查询。");
        expect.setToolEvents(List.of());
        EvalActual leaked = new EvalActual(
                "data_query",
                "该请求涉及数据修改操作，已被安全策略拒绝。系统仅支持只读查询。",
                null,
                false,
                List.of(new EvalActual.ToolEvent("sql.generate", "started", null)));
        EvalComparer.Result failed = comparer.compare(expect, leaked);
        assertFalse(failed.passed());
        assertEquals("toolEvents", failed.mismatch());
        EvalActual blocked = new EvalActual(
                "data_query",
                "该请求涉及数据修改操作，已被安全策略拒绝。系统仅支持只读查询。",
                null,
                false,
                List.of());
        assertTrue(comparer.compare(expect, blocked).passed());
    }
}
