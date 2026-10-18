package com.huiyi.assistant.eval;

import java.util.List;
import java.util.Objects;

public class EvalComparer {

    public record Result(boolean passed, String mismatch) {
        public static Result ok() {
            return new Result(true, null);
        }

        public static Result fail(String mismatch) {
            return new Result(false, mismatch);
        }
    }

    public Result compare(EvalCase.Expect expect, EvalActual actual) {
        if (expect == null) {
            return Result.ok();
        }
        EvalActual safe = actual == null
                ? new EvalActual(null, "", null, false, List.of())
                : actual;

        if (expect.intent() != null && !expect.intent().equals(safe.intent())) {
            return Result.fail("intent");
        }
        if (expect.answerEquals() != null && !expect.answerEquals().equals(safe.answer())) {
            return Result.fail("answerEquals");
        }
        if (expect.answerContains() != null) {
            for (String needle : expect.answerContains()) {
                if (needle != null && !safe.answer().contains(needle)) {
                    return Result.fail("answerContains");
                }
            }
        }
        if (expect.errorCodeSpecified() && !Objects.equals(expect.errorCode(), safe.errorCode())) {
            return Result.fail("errorCode");
        }
        if (expect.analysisHasToolCalls() != null
                && expect.analysisHasToolCalls() != safe.analysisHasToolCalls()) {
            return Result.fail("analysisHasToolCalls");
        }
        if (expect.toolEvents() != null && !expect.toolEvents().isEmpty()
                && !isToolEventPrefix(expect.toolEvents(), safe.toolEvents())) {
            return Result.fail("toolEvents");
        }
        if (expect.forbiddenSubstrings() != null) {
            for (String forbidden : expect.forbiddenSubstrings()) {
                if (forbidden != null && !forbidden.isEmpty() && safe.answer().contains(forbidden)) {
                    return Result.fail("forbiddenSubstrings");
                }
            }
        }
        return Result.ok();
    }

    private boolean isToolEventPrefix(
            List<EvalCase.ToolEventExpect> expected, List<EvalActual.ToolEvent> actual) {
        if (actual.size() < expected.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            EvalCase.ToolEventExpect exp = expected.get(i);
            EvalActual.ToolEvent act = actual.get(i);
            if (exp.name() != null && !exp.name().equals(act.name())) {
                return false;
            }
            if (exp.phase() != null && !exp.phase().equals(act.phase())) {
                return false;
            }
            if (exp.status() != null && !exp.status().equals(act.status())) {
                return false;
            }
        }
        return true;
    }
}
