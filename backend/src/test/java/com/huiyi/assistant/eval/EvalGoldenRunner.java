package com.huiyi.assistant.eval;

import com.huiyi.assistant.agent.AgentRunner;
import com.huiyi.assistant.agent.PromptBudgetExceededException;
import com.huiyi.assistant.agent.RouterPlanner;
import com.huiyi.assistant.agent.ToolExecutor;
import com.huiyi.assistant.agent.ToolNames;
import com.huiyi.assistant.agent.ToolResult;
import com.huiyi.assistant.agent.UnknownToolException;
import com.huiyi.assistant.common.util.PromptTemplateLoader;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import com.huiyi.assistant.model.vo.ReferenceVO;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import com.huiyi.assistant.observability.RequestTraceService;
import com.huiyi.assistant.service.IntentService;
import com.huiyi.assistant.service.LlmService;
import com.huiyi.assistant.service.QueryRewriteService;
import com.huiyi.assistant.service.QuestionDecomposeService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class EvalGoldenRunner {

    private static final Set<String> REQUIRED_IDS = Set.of(
            "scene-s1-knowledge",
            "scene-s2-top10",
            "scene-s3-channel",
            "scene-s4-blocked",
            "scene-s5-nohit",
            "agent-unknown-tool",
            "agent-schema-rejected",
            "agent-tool-event-names",
            "agent-limit-deadline",
            "agent-limit-budget",
            "agent-output-guard",
            "iso-workspace-b-isolated");

    @Test
    void allGoldensPass() throws Exception {
        Path dir = EvalCaseLoader.resolveEvalDir();
        List<EvalCase> cases = new EvalCaseLoader().load(dir);
        assertFalse(cases.isEmpty());
        Set<String> ids = cases.stream().map(EvalCase::id).collect(Collectors.toSet());
        assertTrue(ids.containsAll(REQUIRED_IDS), "missing goldens: " + REQUIRED_IDS.stream()
                .filter(id -> !ids.contains(id)).toList());

        EvalHarness harness = new EvalHarness();
        EvalComparer comparer = new EvalComparer();
        List<EvalReportRow> rows = new ArrayList<>();
        for (EvalCase cse : cases) {
            EvalActual actual = harness.run(cse, runnerFor(cse));
            EvalComparer.Result result = comparer.compare(cse.expect(), actual);
            rows.add(new EvalReportRow(
                    cse.id(),
                    result.passed(),
                    expectedView(cse.expect()),
                    actualView(cse.expect(), actual),
                    result.mismatch()));
        }

        Path json = Path.of("target", "eval-report.json");
        Path md = Path.of("target", "eval-report.md");
        new EvalReportWriter().write(rows, json, md);
        assertTrue(Files.exists(json), "eval-report.json missing");
        assertTrue(Files.exists(md), "eval-report.md missing");

        List<String> failed = rows.stream()
                .filter(row -> !row.passed())
                .map(row -> row.id() + ":" + row.mismatch())
                .toList();
        if (!failed.isEmpty()) {
            fail("eval goldens failed: " + failed);
        }
    }

    private AgentRunner runnerFor(EvalCase cse) {
        QueryRewriteService rewrite = mock(QueryRewriteService.class);
        IntentService intentService = mock(IntentService.class);
        QuestionDecomposeService decompose = mock(QuestionDecomposeService.class);
        RouterPlanner planner = mock(RouterPlanner.class);
        ToolExecutor executor = mock(ToolExecutor.class);
        RequestTraceService trace = mock(RequestTraceService.class);
        LlmService llm = mock(LlmService.class);
        PromptTemplateLoader prompts = mock(PromptTemplateLoader.class);
        ObservabilityMetrics metrics = mock(ObservabilityMetrics.class);
        ExecutorService chatExecutor = mock(ExecutorService.class);

        lenient().when(trace.timed(anyString(), any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(1);
            return action.get();
        });

        IntentTypeEnum intent = "data_query".equals(cse.stubs().intent())
                ? IntentTypeEnum.DATA_QUERY
                : IntentTypeEnum.KNOWLEDGE_QA;
        IntentService.IntentDecision decision =
                new IntentService.IntentDecision(intent, IntentService.Layer.RULE, 1.0);
        if (cse.stubs().budgetExceeded()) {
            lenient().when(rewrite.rewrite(any(), any())).thenThrow(new PromptBudgetExceededException());
        } else {
            String rewritten = cse.stubs().rewrite() != null ? cse.stubs().rewrite() : cse.question();
            lenient().when(rewrite.rewrite(any(), any())).thenReturn(rewritten);
        }
        lenient().when(intentService.detectWithDetail(any())).thenReturn(decision);

        List<String> toolNames = cse.stubs().tools().stream()
                .map(EvalCase.ToolStub::name)
                .toList();
        if (toolNames.isEmpty()) {
            toolNames = intent == IntentTypeEnum.DATA_QUERY
                    ? List.of(ToolNames.SQL_GENERATE)
                    : List.of(ToolNames.KNOWLEDGE_SEARCH);
        }
        List<String> names = toolNames;
        lenient().when(planner.toolsFor(any())).thenReturn(names);

        lenient().when(executor.execute(any(), any(), any()))
                .thenAnswer(invocation -> executeStub(cse, invocation.getArgument(0)));
        lenient().when(prompts.render(anyString(), any())).thenReturn("p");
        lenient().when(llm.chat(any())).thenReturn("eval-summary");

        return new AgentRunner(rewrite, intentService, decompose, planner, executor, trace,
                llm, prompts, metrics, chatExecutor);
    }

    private ToolResult executeStub(EvalCase cse, String name) {
        for (EvalCase.ToolStub stub : cse.stubs().tools()) {
            if (!name.equals(stub.name())) {
                continue;
            }
            if (!stub.ok() && ("unknown".equals(stub.error()) || "unknown_tool".equals(stub.error()))) {
                throw new UnknownToolException(name);
            }
            if (stub.ok()) {
                return ToolResult.ok(adaptData(stub.data()));
            }
            return ToolResult.fail(stub.error() == null ? "eval_stub_missing" : stub.error());
        }
        return ToolResult.fail("eval_stub_missing");
    }

    private Map<String, Object> adaptData(Map<String, Object> data) {
        Map<String, Object> copy = data == null ? new HashMap<>() : new HashMap<>(data);
        Object refs = copy.get("references");
        if (refs instanceof List<?> list) {
            List<ReferenceVO> converted = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof ReferenceVO vo) {
                    converted.add(vo);
                } else if (item instanceof Map<?, ?> map) {
                    Object source = map.get("source");
                    if (source == null) {
                        source = map.get("file");
                    }
                    Object score = map.get("score");
                    Double numeric = score instanceof Number n ? n.doubleValue() : null;
                    converted.add(new ReferenceVO(source == null ? null : String.valueOf(source), numeric));
                }
            }
            copy.put("references", converted);
        }
        return copy;
    }

    private Map<String, Object> expectedView(EvalCase.Expect expect) {
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
        if (expect.toolEvents() != null && !expect.toolEvents().isEmpty()) {
            view.put("toolEvents", expect.toolEvents().stream().map(ev -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", ev.name());
                item.put("phase", ev.phase());
                item.put("status", ev.status());
                return item;
            }).toList());
        }
        if (expect.forbiddenSubstrings() != null && !expect.forbiddenSubstrings().isEmpty()) {
            view.put("forbiddenSubstrings", expect.forbiddenSubstrings());
        }
        return view;
    }

    private Map<String, Object> actualView(EvalCase.Expect expect, EvalActual actual) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (expect == null || actual == null) {
            return view;
        }
        if (expect.intent() != null) {
            view.put("intent", actual.intent());
        }
        if (expect.answerEquals() != null) {
            view.put("answer", actual.answer());
        }
        if (expect.answerContains() != null && !expect.answerContains().isEmpty()) {
            view.put("answer", actual.answer());
        }
        if (expect.errorCodeSpecified()) {
            view.put("errorCode", actual.errorCode());
        }
        if (expect.analysisHasToolCalls() != null) {
            view.put("analysisHasToolCalls", actual.analysisHasToolCalls());
        }
        if (expect.toolEvents() != null && !expect.toolEvents().isEmpty()) {
            view.put("toolEvents", actual.toolEvents().stream().map(ev -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", ev.name());
                item.put("phase", ev.phase());
                item.put("status", ev.status());
                return item;
            }).toList());
        }
        if (expect.forbiddenSubstrings() != null && !expect.forbiddenSubstrings().isEmpty()) {
            view.put("answer", actual.answer());
        }
        return view;
    }
}
