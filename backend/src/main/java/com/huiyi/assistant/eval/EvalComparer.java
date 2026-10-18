package com.huiyi.assistant.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        if (expect.toolEventsSpecified()) {
            List<EvalCase.ToolEventExpect> expectedEvents =
                    expect.toolEvents() == null ? List.of() : expect.toolEvents();
            if (expectedEvents.isEmpty()) {
                if (!safe.toolEvents().isEmpty()) {
                    return Result.fail("toolEvents");
                }
            } else if (!isToolEventPrefix(expectedEvents, safe.toolEvents())) {
                return Result.fail("toolEvents");
            }
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

    public Map<String, Object> expectedView(EvalCase.Expect expect) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (expect == null) {
            return view;
        }
        if (expect.intent() != null) {
            view.put("intent", expect.intent());
        }
        if (expect.answerEquals() != null) {
            view.put("answerEquals", expect.answerEquals());
        }
        if (expect.answerContains() != null && !expect.answerContains().isEmpty()) {
            view.put("answerContains", expect.answerContains());
        }
        if (expect.errorCodeSpecified()) {
            view.put("errorCode", expect.errorCode());
        }
        if (expect.analysisHasToolCalls() != null) {
            view.put("analysisHasToolCalls", expect.analysisHasToolCalls());
        }
        if (expect.toolEventsSpecified()) {
            view.put("toolEvents", eventMaps(expect.toolEvents()));
        }
        if (expect.forbiddenSubstrings() != null && !expect.forbiddenSubstrings().isEmpty()) {
            view.put("forbiddenSubstrings", expect.forbiddenSubstrings());
        }
        return view;
    }

    public Map<String, Object> actualView(EvalCase.Expect expect, EvalActual actual) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (expect == null || actual == null) {
            return view;
        }
        if (expect.intent() != null) {
            view.put("intent", actual.intent());
        }
        if (expect.answerEquals() != null || (expect.answerContains() != null && !expect.answerContains().isEmpty())
                || (expect.forbiddenSubstrings() != null && !expect.forbiddenSubstrings().isEmpty())) {
            view.put("answer", actual.answer());
        }
        if (expect.errorCodeSpecified()) {
            view.put("errorCode", actual.errorCode());
        }
        if (expect.analysisHasToolCalls() != null) {
            view.put("analysisHasToolCalls", actual.analysisHasToolCalls());
        }
        if (expect.toolEventsSpecified()) {
            view.put("toolEvents", actual.toolEvents().stream().map(ev -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", ev.name());
                item.put("phase", ev.phase());
                item.put("status", ev.status());
                return item;
            }).toList());
        }
        return view;
    }

    private List<Map<String, Object>> eventMaps(List<EvalCase.ToolEventExpect> events) {
        if (events == null) {
            return List.of();
        }
        return events.stream().map(ev -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", ev.name());
            item.put("phase", ev.phase());
            item.put("status", ev.status());
            return item;
        }).toList();
    }
}
