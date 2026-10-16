package com.huiyi.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.assistant.common.util.PromptTemplateLoader;
import com.huiyi.assistant.model.dto.AnalysisRequestDTO;
import com.huiyi.assistant.model.dto.DataProfileResponseDTO;
import com.huiyi.assistant.model.dto.SqlValidateResponseDTO;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import com.huiyi.assistant.model.vo.AnalysisResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 数据分析编排服务：Text-to-SQL → 安全校验 → 只读执行 → 画像/图表 → 总结 → 会话落库。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class AnalysisService {

    /** 总结 Prompt 模板位置 */
    private static final String SUMMARY_PROMPT = "prompts/sql-result-summary.md";

    /** 总结时传给 LLM 的最大行数 */
    private static final int SUMMARY_ROWS_LIMIT = 20;

    /** 无法理解问题的话术 */
    private static final String CANNOT_UNDERSTAND = "未能理解您的问题，请换一种问法，例如「上个月销售额最高的 10 个商品是什么？」。";

    /** 安全拦截话术（场景 4） */
    private static final String SAFE_REFUSAL = "该请求涉及数据修改操作，已被安全策略拒绝。系统仅支持只读查询。";

    /** 空结果话术 */
    private static final String EMPTY_RESULT = "查询完成，但没有符合条件的数据。";

    /** 数据修改意图关键词：问题层面直接拦截，不进入 LLM 生成（四道防线之意图层） */
    private static final List<String> MODIFICATION_KEYWORDS = List.of(
            "删除", "修改", "更新", "清空", "插入",
            "delete", "update", "insert", "drop", "truncate", "alter");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TextToSqlService textToSqlService;

    private final SqlExecuteService sqlExecuteService;

    private final PythonServiceClient pythonServiceClient;

    private final LlmService llmService;

    private final PromptTemplateLoader promptTemplateLoader;

    private final ChatSessionService chatSessionService;

    private final QueryRewriteService queryRewriteService;

    public AnalysisService(TextToSqlService textToSqlService, SqlExecuteService sqlExecuteService,
                           PythonServiceClient pythonServiceClient, LlmService llmService,
                           PromptTemplateLoader promptTemplateLoader, ChatSessionService chatSessionService,
                           QueryRewriteService queryRewriteService) {
        this.textToSqlService = textToSqlService;
        this.sqlExecuteService = sqlExecuteService;
        this.pythonServiceClient = pythonServiceClient;
        this.llmService = llmService;
        this.promptTemplateLoader = promptTemplateLoader;
        this.chatSessionService = chatSessionService;
        this.queryRewriteService = queryRewriteService;
    }

    /**
     * 数据分析主链路（含会话解析与落库，供 /api/analysis/query 使用）。
     *
     * @param request 分析请求
     * @return 组装好的分析结果
     */
    public AnalysisResultVO query(AnalysisRequestDTO request) {
        String sessionId = resolveScopedSession(request.getSessionId());
        // 多轮上下文：先结合历史将追问改写为独立问题（无历史时透传）
        String question = queryRewriteService.rewrite(sessionId, request.getQuestion());
        chatSessionService.saveUserMessage(sessionId, request.getQuestion(), IntentTypeEnum.DATA_QUERY);

        AnalysisResultVO result = executeQuery(question);
        result.setSessionId(sessionId);
        chatSessionService.saveAssistantMessage(sessionId, result.getAnswer(),
                IntentTypeEnum.DATA_QUERY, result.getSql(), List.of());
        return result;
    }

    /**
     * 数据分析核心链路（不含会话持久化，供 ChatService 编排复用）。
     *
     * @param question 用户问题
     * @return 组装好的分析结果
     */
    public AnalysisResultVO executeQuery(String question) {
        // 0. 意图层拦截：数据修改意图直接拒绝，不进入 LLM 生成（场景 4）
        if (containsModificationIntent(question)) {
            log.warn("数据修改意图被拦截: question={}", question);
            return finish(question, null, List.of(), null, SAFE_REFUSAL);
        }

        // 1. Text-to-SQL
        String sql = textToSqlService.generateSql(question);
        if (sql == null) {
            return finish(question, null, List.of(), null, CANNOT_UNDERSTAND);
        }
        log.info("生成 SQL: {}", sql);

        // 2. 安全校验（四道防线之静态校验）
        SqlValidateResponseDTO validation = pythonServiceClient.validateSql(sql);
        if (validation == null || !Boolean.TRUE.equals(validation.getValid())) {
            String reason = validation == null ? "校验服务异常" : validation.getReason();
            log.warn("SQL 被安全策略拦截: sql={}, reason={}", sql, reason);
            return finish(question, sql, List.of(), null, refusalFor(reason));
        }
        String safeSql = validation.getSql();

        // 3. 只读执行（超时 5s、500 行上限、审计日志）
        List<Map<String, Object>> rows = sqlExecuteService.execute(safeSql, question);

        // 4. 数据画像与图表推荐
        DataProfileResponseDTO profile = pythonServiceClient.profileData(rows);

        // 5. 结果总结（空结果走确定性话术，不调用 LLM）
        String answer;
        if (rows.isEmpty()) {
            answer = EMPTY_RESULT;
        } else {
            answer = summarize(question, safeSql, profile, rows);
        }

        AnalysisResultVO.Summary summary = AnalysisResultVO.Summary.builder()
                .rowCount(profile == null ? rows.size() : profile.getRowCount())
                .chartType(profile == null ? "table" : profile.getChartType())
                .build();
        return finish(question, safeSql, rows, summary, answer);
    }

    /**
     * 调用 LLM 总结查询结果。
     */
    private String summarize(String question, String sql, DataProfileResponseDTO profile,
                             List<Map<String, Object>> rows) {
        String prompt = promptTemplateLoader.render(SUMMARY_PROMPT, Map.of(
                "question", question,
                "sql", sql,
                "profile", toJson(profile),
                "rows", toJson(rows.stream().limit(SUMMARY_ROWS_LIMIT).toList())));
        return llmService.chat(prompt);
    }

    /**
     * 判断问题是否包含数据修改意图（大小写不敏感）。
     */
    private boolean containsModificationIntent(String question) {
        String lower = question.toLowerCase();
        return MODIFICATION_KEYWORDS.stream().anyMatch(lower::contains);
    }

    /**
     * 按拦截原因给出友好话术。
     */
    private String refusalFor(String reason) {
        if (reason == null) {
            return SAFE_REFUSAL;
        }
        if (reason.contains("白名单")) {
            return "该请求访问的表不在允许范围内，系统仅支持销售业务数据查询。";
        }
        return SAFE_REFUSAL;
    }

    /**
     * 组装结果。
     */
    private AnalysisResultVO finish(String question, String sql,
                                    List<Map<String, Object>> rows, AnalysisResultVO.Summary summary,
                                    String answer) {
        return AnalysisResultVO.builder()
                .question(question)
                .sql(sql)
                .rows(rows)
                .summary(summary)
                .answer(answer)
                .build();
    }

    private String resolveScopedSession(String sessionId) {
        com.huiyi.assistant.context.WorkspaceContext context = com.huiyi.assistant.context.WorkspaceContextHolder.get();
        if (context == null) {
            return chatSessionService.resolveSessionId(sessionId);
        }
        return chatSessionService.ensureSession(context.workspaceId(), context.userId(), sessionId);
    }

    /**
     * 对象转 JSON 字符串（失败时返回空对象）。
     */
    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
