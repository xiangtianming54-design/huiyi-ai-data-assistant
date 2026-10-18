package com.huiyi.assistant.eval;

import com.huiyi.assistant.agent.AgentRunner;

import java.util.Map;

public class EvalCaseExecutor {

    private final EvalHarness harness;
    private final EvalComparer comparer;

    public EvalCaseExecutor() {
        this(new EvalHarness(), new EvalComparer());
    }

    public EvalCaseExecutor(EvalHarness harness, EvalComparer comparer) {
        this.harness = harness;
        this.comparer = comparer;
    }

    public EvalReportRow execute(EvalCase cse, AgentRunner runner) {
        try {
            EvalActual actual = harness.run(cse, runner);
            EvalComparer.Result result = comparer.compare(cse.expect(), actual);
            return new EvalReportRow(
                    cse.id(),
                    result.passed(),
                    comparer.expectedView(cse.expect()),
                    comparer.actualView(cse.expect(), actual),
                    result.mismatch());
        } catch (RuntimeException e) {
            return new EvalReportRow(cse.id(), false, Map.of(), Map.of(), "harnessError");
        }
    }
}
