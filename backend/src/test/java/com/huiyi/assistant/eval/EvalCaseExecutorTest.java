package com.huiyi.assistant.eval;

import com.huiyi.assistant.agent.AgentRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class EvalCaseExecutorTest {

    @Test
    void harnessErrorRecordsFailedRow() {
        EvalCase cse = new EvalCase();
        cse.setId("boom");
        cse.setEntrypoint("analysis_query");
        cse.setQuestion("q");
        AgentRunner runner = mock(AgentRunner.class);
        org.mockito.Mockito.when(runner.run(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("boom"));
        EvalReportRow row = new EvalCaseExecutor().execute(cse, runner);
        assertFalse(row.passed());
        assertEquals("boom", row.id());
        assertEquals("harnessError", row.mismatch());
    }
}
