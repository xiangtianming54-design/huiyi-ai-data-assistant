package com.huiyi.assistant.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.assistant.common.result.ResultCode;
import com.huiyi.assistant.common.util.PromptTemplateLoader;
import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.context.WorkspaceContextHolder;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import com.huiyi.assistant.model.vo.AnalysisResultVO;
import com.huiyi.assistant.model.vo.ReferenceVO;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import com.huiyi.assistant.observability.RequestTraceService;
import com.huiyi.assistant.service.IntentService;
import com.huiyi.assistant.service.LlmService;
import com.huiyi.assistant.service.QueryRewriteService;
import com.huiyi.assistant.service.QuestionDecomposeService;
import com.huiyi.assistant.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
public class AgentRunner {

    public static final String UNKNOWN_TOOL_USER_MESSAGE = "工具调用失败，请稍后重试。";
    public static final String LIMIT_USER_MESSAGE = "当前问题较复杂，请缩小范围后重试。";
    public static final String ERROR_AGENT_LIMIT = "AGENT_LIMIT_EXCEEDED";
    static final String RUN_TIMEOUT_INTERNAL = "run_timeout";

    private static final String SUMMARY_PROMPT = "prompts/sql-result-summary.md";
    private static final int SUMMARY_ROWS_LIMIT = 20;
    private static final String CANNOT_UNDERSTAND = "未能理解您的问题，请换一种问法，例如「上个月销售额最高的 10 个商品是什么？」。";
    private static final String SAFE_REFUSAL = "该请求涉及数据修改操作，已被安全策略拒绝。系统仅支持只读查询。";
    private static final String EMPTY_RESULT = "查询完成，但没有符合条件的数据。";
    private static final String SQL_EXECUTE_FAIL = "SQL 执行失败，请调整问题后重试";
    private static final List<String> MODIFICATION_KEYWORDS = List.of(
            "删除", "修改", "更新", "清空", "插入",
            "delete", "update", "insert", "drop", "truncate", "alter");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final QueryRewriteService queryRewriteService;
    private final IntentService intentService;
    private final QuestionDecomposeService questionDecomposeService;
    private final RouterPlanner routerPlanner;
    private final ToolExecutor toolExecutor;
    private final RequestTraceService requestTraceService;
    private final LlmService llmService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ObservabilityMetrics observabilityMetrics;
    private final ExecutorService chatExecutor;

    public AgentRunner(QueryRewriteService queryRewriteService, IntentService intentService,
                       QuestionDecomposeService questionDecomposeService, RouterPlanner routerPlanner,
                       ToolExecutor toolExecutor, RequestTraceService requestTraceService,
                       LlmService llmService, PromptTemplateLoader promptTemplateLoader,
                       ObservabilityMetrics observabilityMetrics,
                       @Qualifier("chatExecutor") ExecutorService chatExecutor) {
        this.queryRewriteService = queryRewriteService;
        this.intentService = intentService;
        this.questionDecomposeService = questionDecomposeService;
        this.routerPlanner = routerPlanner;
        this.toolExecutor = toolExecutor;
        this.requestTraceService = requestTraceService;
        this.llmService = llmService;
        this.promptTemplateLoader = promptTemplateLoader;
        this.observabilityMetrics = observabilityMetrics;
        this.chatExecutor = chatExecutor;
    }

    public ToolResult invoke(String toolName, Map<String, Object> arguments) {
        return invoke(toolName, arguments, "", ToolEventListener.NOOP, AgentDeadline.fromNow(90_000L));
    }

    private ToolResult invoke(String toolName, Map<String, Object> arguments, String question,
                              ToolEventListener listener, AgentDeadline deadline) {
        ToolEventListener safeListener = listener == null ? ToolEventListener.NOOP : listener;
        if (deadline != null && deadline.expired()) {
            notifyFailed(safeListener, toolName, "FAILED");
            observabilityMetrics.agentLimitExceeded();
            return ToolResult.fail(RUN_TIMEOUT_INTERNAL);
        }
        notifyStarted(safeListener, toolName);
        ToolContext context = toolContextForQuestion(question);
        long start = System.nanoTime();
        try {
            ToolResult result = toolExecutor.execute(toolName, context, arguments);
            if (!result.ok() && ToolSchemaValidator.SCHEMA_INVALID.equals(result.error())) {
                observabilityMetrics.toolSchemaRejected();
                requestTraceService.stepTool(toolName, "REJECTED", start, result.error());
                notifyFailed(safeListener, toolName, "REJECTED");
                return ToolResult.fail(UNKNOWN_TOOL_USER_MESSAGE);
            }
            String status = stepStatus(toolName, result);
            requestTraceService.stepTool(toolName, status, start, stepError(toolName, result));
            if ("SUCCESS".equals(status)) {
                notifyCompleted(safeListener, toolName, status);
            } else {
                notifyFailed(safeListener, toolName, status);
            }
            return result;
        } catch (UnknownToolException e) {
            requestTraceService.stepTool(e.toolName(), "FAILED", start, e.getMessage());
            notifyFailed(safeListener, e.toolName(), "FAILED");
            return ToolResult.fail(UNKNOWN_TOOL_USER_MESSAGE);
        }
    }

    public AgentResult run(AgentRequest request) {
        return run(request, ToolEventListener.NOOP, AgentDeadline.fromNow(90_000L));
    }

    public AgentResult run(AgentRequest request, ToolEventListener listener, AgentDeadline deadline) {
        ToolEventListener safeListener = listener == null ? ToolEventListener.NOOP : listener;
        AgentDeadline safeDeadline = deadline == null ? AgentDeadline.fromNow(90_000L) : deadline;
        String sessionId = request.sessionId();
        try {
            String rewritten = requestTraceService.timed("query_rewrite",
                    () -> queryRewriteService.rewrite(sessionId, request.question()));
            IntentService.IntentDecision decision = requestTraceService.timed("intent",
                    () -> intentService.detectWithDetail(rewritten));
            return runIntent(request.entrypoint(), rewritten, decision, safeListener, safeDeadline);
        } catch (PromptBudgetExceededException e) {
            observabilityMetrics.agentLimitExceeded();
            IntentService.IntentDecision decision = intentService.detectWithDetail(request.question());
            return limitResult(decision);
        }
    }

    public List<AgentResult> runAsk(AgentRequest request) {
        String sessionId = request.sessionId();
        String rewritten = requestTraceService.timed("query_rewrite",
                () -> queryRewriteService.rewrite(sessionId, request.question()));
        List<String> subQuestions = requestTraceService.timed("decompose",
                () -> questionDecomposeService.decompose(rewritten));
        if (subQuestions.size() == 1) {
                    IntentService.IntentDecision decision = requestTraceService.timed("intent",
                            () -> intentService.detectWithDetail(subQuestions.get(0)));
            return List.of(runIntent(request.entrypoint(), subQuestions.get(0), decision,
                    ToolEventListener.NOOP, AgentDeadline.fromNow(90_000L)));
        }
        List<CompletableFuture<AgentResult>> futures = subQuestions.stream()
                .map(sub -> CompletableFuture.supplyAsync(() -> {
                    IntentService.IntentDecision decision = requestTraceService.timed("intent",
                            () -> intentService.detectWithDetail(sub));
                    return runIntent(request.entrypoint(), sub, decision,
                            ToolEventListener.NOOP, AgentDeadline.fromNow(90_000L));
                }, chatExecutor))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private AgentResult runIntent(String entrypoint, String question, IntentService.IntentDecision decision,
                                  ToolEventListener listener, AgentDeadline deadline) {
        if (decision.intent() == IntentTypeEnum.DATA_QUERY) {
            return runDataQuery(question, decision, listener, deadline);
        }
        return runKnowledge(entrypoint, question, decision, listener, deadline);
    }

    private AgentResult runKnowledge(String entrypoint, String question, IntentService.IntentDecision decision,
                                     ToolEventListener listener, AgentDeadline deadline) {
        String searchName = routerPlanner.toolsFor(IntentTypeEnum.KNOWLEDGE_QA).get(0);
        ToolResult search = invoke(searchName, Map.of(), question, listener, deadline);
        if (isRunTimeout(search)) {
            return limitResult(decision);
        }
        RagService.RagContext ragContext = toRagContext(search);
        boolean hit = ragContext.references() != null && !ragContext.references().isEmpty();
        observabilityMetrics.ragSearch(java.time.Duration.ZERO, hit,
                RagService.SEARCH_DEGRADED_FALLBACK.equals(ragContext.fallbackAnswer()));
        if ("chat_stream".equals(entrypoint) && ragContext.needLlm()) {
            return new AgentResult(decision, null, null, ragContext.references(), null, ragContext, null);
        }
        String answer = ragContext.needLlm() ? llmService.chat(ragContext.prompt()) : ragContext.fallbackAnswer();
        return new AgentResult(decision, answer, null, ragContext.references(), null, ragContext, null);
    }

    private AgentResult runDataQuery(String question, IntentService.IntentDecision decision,
                                     ToolEventListener listener, AgentDeadline deadline) {
        if (containsModificationIntent(question)) {
            log.warn("数据修改意图被拦截: question={}", question);
            observabilityMetrics.sqlValidate(false);
            requestTraceService.step("sql_validate", "REJECTED", System.nanoTime(), "modification_intent");
            return analysisResult(decision, question, null, List.of(), null, SAFE_REFUSAL);
        }
        String sql = null;
        String safeSql = null;
        List<Map<String, Object>> rows = List.of();
        ToolResult profiled = ToolResult.ok(Map.of());
        for (String name : routerPlanner.toolsFor(IntentTypeEnum.DATA_QUERY)) {
            if (ToolNames.SQL_GENERATE.equals(name)) {
                ToolResult generated = invoke(name, Map.of(), question, listener, deadline);
                if (isRunTimeout(generated)) {
                    return limitResult(decision);
                }
                if (isTimeout(generated)) {
                    return analysisResult(decision, question, null, List.of(), null,
                            ResultCode.LLM_SERVICE_ERROR.getMessage());
                }
                sql = generated.ok() ? stringVal(generated.data().get("sql")) : null;
                observabilityMetrics.sqlGenerate(sql != null);
                if (sql == null) {
                    return analysisResult(decision, question, null, List.of(), null, CANNOT_UNDERSTAND);
                }
            } else if (ToolNames.SQL_VALIDATE.equals(name)) {
                ToolResult validated = invoke(name, Map.of("sql", sql), question, listener, deadline);
                if (isRunTimeout(validated)) {
                    return limitResult(decision);
                }
                if (isTimeout(validated)) {
                    return analysisResult(decision, question, sql, List.of(), null,
                            ResultCode.PYTHON_SERVICE_UNAVAILABLE.getMessage());
                }
                boolean passed = validated.ok() && Boolean.TRUE.equals(validated.data().get("valid"));
                observabilityMetrics.sqlValidate(passed);
                if (!passed) {
                    if (!validated.ok()) {
                        return analysisResult(decision, question, sql, List.of(), null, "校验服务异常");
                    }
                    String reason = stringVal(validated.data().get("reason"));
                    return analysisResult(decision, question, sql, List.of(), null, refusalFor(reason));
                }
                safeSql = stringVal(validated.data().get("sql"));
                if (safeSql == null) {
                    safeSql = sql;
                }
            } else if (ToolNames.SQL_EXECUTE_READONLY.equals(name)) {
                ToolResult executed = invoke(name, Map.of("sql", safeSql == null ? sql : safeSql), question,
                        listener, deadline);
                if (isRunTimeout(executed)) {
                    return limitResult(decision);
                }
                if (!executed.ok()) {
                    String copy = isTimeout(executed) ? SQL_EXECUTE_FAIL : executed.error();
                    return analysisResult(decision, question, safeSql, List.of(), null, copy);
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> executedRows = executed.data().get("rows") instanceof List<?> list
                        ? (List<Map<String, Object>>) list
                        : List.of();
                rows = executedRows;
            } else if (ToolNames.DATA_PROFILE.equals(name)) {
                profiled = invoke(name, Map.of("rows", rows), question, listener, deadline);
                if (isRunTimeout(profiled)) {
                    return limitResult(decision);
                }
            }
        }
        String chartType = profiled.ok() ? stringVal(profiled.data().get("chartType")) : "table";
        Integer rowCount = profiled.ok() && profiled.data().get("rowCount") instanceof Number n
                ? n.intValue() : rows.size();
        AnalysisResultVO.Summary summary = AnalysisResultVO.Summary.builder()
                .rowCount(rowCount)
                .chartType(chartType == null ? "table" : chartType)
                .build();
        String answer;
        if (rows.isEmpty()) {
            answer = EMPTY_RESULT;
        } else {
            String sqlForSummary = safeSql;
            Map<String, Object> profileData = profiled.data();
            List<Map<String, Object>> summaryRows = rows;
            answer = requestTraceService.timed("llm_summarize",
                    () -> summarize(question, sqlForSummary, profileData, summaryRows));
        }
        return analysisResult(decision, question, safeSql, rows, summary, answer);
    }

    private String summarize(String question, String sql, Map<String, Object> profile,
                             List<Map<String, Object>> rows) {
        ContextBudget budget = new ContextBudget();
        List<Map<String, Object>> limited = new ArrayList<>(
                rows == null ? List.of() : rows.stream().limit(SUMMARY_ROWS_LIMIT).toList());
        String profileJson = budget.trimSchema(toJson(profile));
        String rowsJson = toJson(limited);
        while (limited.size() > 1 && profileJson.length() + rowsJson.length() > 24_000) {
            limited.remove(limited.size() - 1);
            rowsJson = toJson(limited);
        }
        rowsJson = budget.trimHistory(rowsJson);
        budget.assertFits("", "", profileJson, rowsJson, question);
        String prompt = promptTemplateLoader.render(SUMMARY_PROMPT, Map.of(
                "question", question,
                "sql", sql == null ? "" : sql,
                "profile", profileJson,
                "rows", rowsJson));
        return llmService.chat(prompt);
    }

    private AgentResult analysisResult(IntentService.IntentDecision decision, String question, String sql,
                                       List<Map<String, Object>> rows, AnalysisResultVO.Summary summary,
                                       String answer) {
        AnalysisResultVO analysis = AnalysisResultVO.builder()
                .question(question)
                .sql(sql)
                .rows(rows)
                .summary(summary)
                .answer(answer)
                .build();
        return new AgentResult(decision, answer, sql, List.of(), analysis, null, null);
    }

    private AgentResult limitResult(IntentService.IntentDecision decision) {
        AnalysisResultVO analysis = AnalysisResultVO.builder()
                .question(null)
                .sql(null)
                .rows(List.of())
                .summary(null)
                .answer(LIMIT_USER_MESSAGE)
                .build();
        return new AgentResult(decision, LIMIT_USER_MESSAGE, null, List.of(), analysis, null, ERROR_AGENT_LIMIT);
    }

    private ToolContext toolContextForQuestion(String question) {
        WorkspaceContext workspace = WorkspaceContextHolder.get();
        long workspaceId = workspace == null ? 0L : workspace.workspaceId();
        long userId = workspace == null ? 0L : workspace.userId();
        return new ToolContext(workspaceId, userId, question);
    }

    @SuppressWarnings("unchecked")
    private RagService.RagContext toRagContext(ToolResult search) {
        if (!search.ok()) {
            return new RagService.RagContext(UNKNOWN_TOOL_USER_MESSAGE, null, List.of());
        }
        String fallback = stringVal(search.data().get("fallbackAnswer"));
        String prompt = stringVal(search.data().get("prompt"));
        List<ReferenceVO> references = search.data().get("references") instanceof List<?> list
                ? (List<ReferenceVO>) list
                : List.of();
        return new RagService.RagContext(fallback, prompt, references);
    }

    private boolean containsModificationIntent(String question) {
        String lower = question.toLowerCase();
        return MODIFICATION_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private static boolean isTimeout(ToolResult result) {
        return result != null && !result.ok() && ToolExecutor.TIMEOUT_ERROR.equals(result.error());
    }

    private static boolean isRunTimeout(ToolResult result) {
        return result != null && !result.ok() && RUN_TIMEOUT_INTERNAL.equals(result.error());
    }

    private static void notifyStarted(ToolEventListener listener, String toolName) {
        try {
            listener.onStarted(toolName);
        } catch (RuntimeException e) {
            log.warn("tool listener onStarted failed tool={}", toolName, e);
        }
    }

    private static void notifyCompleted(ToolEventListener listener, String toolName, String status) {
        try {
            listener.onCompleted(toolName, status);
        } catch (RuntimeException e) {
            log.warn("tool listener onCompleted failed tool={}", toolName, e);
        }
    }

    private static void notifyFailed(ToolEventListener listener, String toolName, String status) {
        try {
            listener.onFailed(toolName, status);
        } catch (RuntimeException e) {
            log.warn("tool listener onFailed failed tool={}", toolName, e);
        }
    }

    private static String stepStatus(String toolName, ToolResult result) {
        if (!result.ok()) {
            if (ToolSchemaValidator.SCHEMA_INVALID.equals(result.error())) {
                return "REJECTED";
            }
            return "FAILED";
        }
        if (Boolean.FALSE.equals(result.data().get("valid"))) {
            return "REJECTED";
        }
        if (ToolNames.SQL_GENERATE.equals(toolName) && result.data().get("sql") == null) {
            return "FAILED";
        }
        return "SUCCESS";
    }

    private static String stepError(String toolName, ToolResult result) {
        if (!result.ok()) {
            return result.error();
        }
        if (Boolean.FALSE.equals(result.data().get("valid"))) {
            return stringVal(result.data().get("reason"));
        }
        if (ToolNames.SQL_GENERATE.equals(toolName) && result.data().get("sql") == null) {
            return "empty_sql";
        }
        return null;
    }

    private String refusalFor(String reason) {
        if (reason != null && reason.contains("白名单")) {
            return "该请求访问的表不在允许范围内，系统仅支持销售业务数据查询。";
        }
        return SAFE_REFUSAL;
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
