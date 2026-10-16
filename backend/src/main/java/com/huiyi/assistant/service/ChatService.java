package com.huiyi.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.assistant.model.dto.ChatRequestDTO;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import com.huiyi.assistant.model.vo.AnalysisResultVO;
import com.huiyi.assistant.model.vo.ChatAskResponseVO;
import com.huiyi.assistant.model.vo.ChatPartVO;
import com.huiyi.assistant.model.vo.ChatResponseVO;
import com.huiyi.assistant.model.vo.IntentResponseVO;
import com.huiyi.assistant.model.vo.ReferenceVO;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import com.huiyi.assistant.observability.RequestTraceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 问答编排服务：意图识别 → RAG / 数据查询 → 回答组装 → 会话落库。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class ChatService {

    /** SSE 连接超时 */
    private static final long SSE_TIMEOUT_MS = Duration.ofSeconds(90).toMillis();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IntentService intentService;

    private final RagService ragService;

    private final LlmService llmService;

    private final AnalysisService analysisService;

    private final ChatSessionService chatSessionService;

    private final QueryRewriteService queryRewriteService;

    private final QuestionDecomposeService questionDecomposeService;

    private final ExecutorService chatExecutor;
    private final RequestTraceService requestTraceService;
    private final ObservabilityMetrics observabilityMetrics;

    public ChatService(IntentService intentService, RagService ragService,
                       LlmService llmService, AnalysisService analysisService,
                       ChatSessionService chatSessionService, QueryRewriteService queryRewriteService,
                       QuestionDecomposeService questionDecomposeService, ExecutorService chatExecutor,
                       RequestTraceService requestTraceService, ObservabilityMetrics observabilityMetrics) {
        this.intentService = intentService;
        this.ragService = ragService;
        this.llmService = llmService;
        this.analysisService = analysisService;
        this.chatSessionService = chatSessionService;
        this.queryRewriteService = queryRewriteService;
        this.questionDecomposeService = questionDecomposeService;
        this.chatExecutor = chatExecutor;
        this.requestTraceService = requestTraceService;
        this.observabilityMetrics = observabilityMetrics;
    }

    /**
     * 意图预检：供前端统一对话入口路由（知识问答走 SSE，数据查询走分析接口）。
     *
     * @param question 用户问题
     * @return 意图类型
     */
    public IntentTypeEnum detectIntent(String question) {
        return intentService.detect(question);
    }

    /**
     * 意图预检（含复合问题识别）：问号/分号启发式疑似复合时调用分解服务。
     *
     * @param question 用户问题
     * @return 预检结果（compound 时携带子问题列表）
     */
    public IntentResponseVO precheck(String question) {
        if (isSuspectedCompound(question)) {
            List<String> subQuestions = questionDecomposeService.decompose(question);
            if (subQuestions.size() > 1) {
                return new IntentResponseVO("compound", subQuestions);
            }
        }
        return new IntentResponseVO(intentService.detect(question).getCode(), null);
    }

    /**
     * 复合问题启发式：问号/分号 ≥ 2 个。
     */
    private boolean isSuspectedCompound(String question) {
        if (question == null) {
            return false;
        }
        return question.chars().filter(c -> c == '？' || c == '?' || c == '；' || c == ';').count() >= 2;
    }

    /**
     * 统一问答编排：改写 → 分解 → 单/多路执行 → 多部分响应。
     *
     * @param request 问答请求
     * @return 统一问答响应（parts 长度 1~2）
     */
    public ChatAskResponseVO ask(ChatRequestDTO request) {
        observabilityMetrics.chatRequest();
        long start = System.nanoTime();
        requestTraceService.start("chat_ask", request.getQuestion(), request.getSessionId());
        try {
            String sessionId = resolveScopedSession(request.getSessionId());
            String question = requestTraceService.timed("query_rewrite",
                    () -> queryRewriteService.rewrite(sessionId, request.getQuestion()));
            List<String> subQuestions = requestTraceService.timed("decompose",
                    () -> questionDecomposeService.decompose(question));

            ChatAskResponseVO response;
            if (subQuestions.size() == 1) {
                String single = subQuestions.get(0);
                IntentService.IntentDecision decision = requestTraceService.timed("intent",
                        () -> intentService.detectWithDetail(single));
                chatSessionService.saveUserMessage(sessionId, request.getQuestion(), decision.intent());
                response = ChatAskResponseVO.builder()
                        .sessionId(sessionId)
                        .parts(List.of(executeSubQuestion(sessionId, single, decision)))
                        .build();
            } else {
                log.info("复合问题并行执行: sessionId={}, parts={}", sessionId, subQuestions.size());
                chatSessionService.saveUserMessage(sessionId, request.getQuestion(), null);
                List<CompletableFuture<ChatPartVO>> futures = subQuestions.stream()
                        .map(sub -> CompletableFuture.supplyAsync(
                                () -> executeSubQuestion(sessionId, sub,
                                        requestTraceService.timed("intent", () -> intentService.detectWithDetail(sub))),
                                chatExecutor))
                        .toList();
                List<ChatPartVO> parts = futures.stream().map(CompletableFuture::join).toList();
                response = ChatAskResponseVO.builder().sessionId(sessionId).parts(parts).build();
            }
            requestTraceService.finish("SUCCEEDED", null, null, start);
            observabilityMetrics.chatSuccess(Duration.ofNanos(System.nanoTime() - start), null);
            return response;
        } catch (RuntimeException e) {
            requestTraceService.finish("FAILED", null, e.getMessage(), start);
            observabilityMetrics.chatFailed(Duration.ofNanos(System.nanoTime() - start));
            throw e;
        }
    }

    /**
     * 执行单个子问题并按意图落库，返回统一的部分结果。
     */
    private ChatPartVO executeSubQuestion(String sessionId, String question,
                                          IntentService.IntentDecision decision) {
        String layer = decision.layer().name().toLowerCase();
        if (decision.intent() == IntentTypeEnum.DATA_QUERY) {
            AnalysisResultVO result = requestTraceService.timed("sql_pipeline",
                    () -> analysisService.executeQuery(question));
            chatSessionService.saveAssistantMessage(sessionId, result.getAnswer(),
                    IntentTypeEnum.DATA_QUERY, result.getSql(), List.of(), layer, decision.score());
            return ChatPartVO.builder()
                    .question(question)
                    .intent(IntentTypeEnum.DATA_QUERY.getCode())
                    .answer(result.getAnswer())
                    .references(List.of())
                    .analysis(result)
                    .build();
        }
        RagService.RagAnswer ragAnswer = timedRagAnswer(question);
        chatSessionService.saveAssistantMessage(sessionId, ragAnswer.answer(),
                IntentTypeEnum.KNOWLEDGE_QA, null, ragAnswer.references(), layer, decision.score());
        return ChatPartVO.builder()
                .question(question)
                .intent(IntentTypeEnum.KNOWLEDGE_QA.getCode())
                .answer(ragAnswer.answer())
                .references(ragAnswer.references())
                .build();
    }

    /**
     * 非流式问答。
     *
     * @param request 问答请求
     * @return 问答响应
     */
    public ChatResponseVO chat(ChatRequestDTO request) {
        observabilityMetrics.chatRequest();
        long start = System.nanoTime();
        requestTraceService.start("chat", request.getQuestion(), request.getSessionId());
        try {
            String sessionId = resolveScopedSession(request.getSessionId());
            String question = requestTraceService.timed("query_rewrite",
                    () -> queryRewriteService.rewrite(sessionId, request.getQuestion()));
            IntentService.IntentDecision decision = requestTraceService.timed("intent",
                    () -> intentService.detectWithDetail(question));
            IntentTypeEnum intent = decision.intent();
            chatSessionService.saveUserMessage(sessionId, request.getQuestion(), intent);

            String answer;
            String sqlText = null;
            List<ReferenceVO> references;
            if (intent == IntentTypeEnum.DATA_QUERY) {
                AnalysisResultVO result = requestTraceService.timed("sql_pipeline",
                        () -> analysisService.executeQuery(question));
                answer = result.getAnswer();
                sqlText = result.getSql();
                references = List.of();
            } else {
                RagService.RagAnswer ragAnswer = timedRagAnswer(question);
                answer = ragAnswer.answer();
                references = ragAnswer.references();
            }

            chatSessionService.saveAssistantMessage(sessionId, answer, intent, sqlText, references,
                    decision.layer().name().toLowerCase(), decision.score());
            requestTraceService.finish("SUCCEEDED", intent.getCode(), null, start);
            observabilityMetrics.chatSuccess(Duration.ofNanos(System.nanoTime() - start), null);
            return ChatResponseVO.builder()
                    .sessionId(sessionId)
                    .answer(answer)
                    .intent(intent.getCode())
                    .references(references)
                    .build();
        } catch (RuntimeException e) {
            requestTraceService.finish("FAILED", null, e.getMessage(), start);
            observabilityMetrics.chatFailed(Duration.ofNanos(System.nanoTime() - start));
            throw e;
        }
    }

    /**
     * 流式问答：SSE 逐 token 推送，结束时推送引用来源，完成后会话落库。
     *
     * @param request 问答请求
     * @return SSE 发射器
     */
    public SseEmitter chatStream(ChatRequestDTO request) {
        observabilityMetrics.chatRequest();
        long start = System.nanoTime();
        requestTraceService.start("chat_stream", request.getQuestion(), request.getSessionId());
        String sessionId = resolveScopedSession(request.getSessionId());
        String question = requestTraceService.timed("query_rewrite",
                () -> queryRewriteService.rewrite(sessionId, request.getQuestion()));
        IntentService.IntentDecision decision = requestTraceService.timed("intent",
                () -> intentService.detectWithDetail(question));
        IntentTypeEnum intent = decision.intent();
        chatSessionService.saveUserMessage(sessionId, request.getQuestion(), intent);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sendEvent(emitter, "session", sessionId);

        Flux<String> tokenFlux;
        List<ReferenceVO> references;
        String sqlText = null;
        if (intent == IntentTypeEnum.DATA_QUERY) {
            AnalysisResultVO result = requestTraceService.timed("sql_pipeline",
                    () -> analysisService.executeQuery(question));
            sqlText = result.getSql();
            tokenFlux = Flux.just(result.getAnswer());
            references = List.of();
        } else {
            long ragStart = System.nanoTime();
            RagService.RagContext context = ragService.prepare(question, workspaceId());
            boolean hit = context.references() != null && !context.references().isEmpty();
            observabilityMetrics.ragSearch(Duration.ofNanos(System.nanoTime() - ragStart), hit,
                    context.fallbackAnswer() != null && context.fallbackAnswer().equals(RagService.SEARCH_DEGRADED_FALLBACK));
            requestTraceService.step("rag", "SUCCESS", ragStart, null);
            references = context.references();
            tokenFlux = ragService.stream(context, llmService);
        }

        final String finalSqlText = sqlText;
        StringBuilder fullAnswer = new StringBuilder();
        List<ReferenceVO> finalReferences = references;
        AtomicBoolean firstToken = new AtomicBoolean(true);
        AtomicLong firstTokenAt = new AtomicLong();
        tokenFlux.subscribe(
                token -> {
                    if (firstToken.compareAndSet(true, false)) {
                        firstTokenAt.set(System.nanoTime());
                    }
                    fullAnswer.append(token);
                    sendEvent(emitter, "token", token);
                },
                error -> {
                    log.warn("流式问答中断: {}", error.getMessage());
                    String partial = fullAnswer.toString();
                    sendEvent(emitter, "error", "AI 服务暂不可用，请稍后重试。");
                    emitter.complete();
                    chatSessionService.saveAssistantMessage(sessionId,
                            partial.isEmpty() ? "AI 服务暂不可用，请稍后重试。" : partial,
                            intent, null, finalReferences,
                            decision.layer().name().toLowerCase(), decision.score());
                    requestTraceService.finish("FAILED", intent.getCode(), error.getMessage(), start);
                    observabilityMetrics.chatFailed(Duration.ofNanos(System.nanoTime() - start));
                },
                () -> {
                    sendEvent(emitter, "references", toJson(finalReferences));
                    emitter.complete();
                    chatSessionService.saveAssistantMessage(sessionId, fullAnswer.toString(),
                            intent, finalSqlText, finalReferences,
                            decision.layer().name().toLowerCase(), decision.score());
                    requestTraceService.finish("SUCCEEDED", intent.getCode(), null, start);
                    Duration first = firstTokenAt.get() == 0 ? null : Duration.ofNanos(firstTokenAt.get() - start);
                    observabilityMetrics.chatSuccess(Duration.ofNanos(System.nanoTime() - start), first);
                });
        return emitter;
    }

    private RagService.RagAnswer timedRagAnswer(String question) {
        long ragStart = System.nanoTime();
        RagService.RagAnswer ragAnswer = ragService.answer(question, llmService, workspaceId());
        boolean hit = ragAnswer.references() != null && !ragAnswer.references().isEmpty();
        observabilityMetrics.ragSearch(Duration.ofNanos(System.nanoTime() - ragStart), hit, false);
        requestTraceService.step("rag", "SUCCESS", ragStart, null);
        return ragAnswer;
    }

    private String resolveScopedSession(String sessionId) {
        com.huiyi.assistant.context.WorkspaceContext context = com.huiyi.assistant.context.WorkspaceContextHolder.get();
        if (context == null) {
            return chatSessionService.resolveSessionId(sessionId);
        }
        return chatSessionService.ensureSession(context.workspaceId(), context.userId(), sessionId);
    }

    private Long workspaceId() {
        com.huiyi.assistant.context.WorkspaceContext context = com.huiyi.assistant.context.WorkspaceContextHolder.get();
        return context == null ? null : context.workspaceId();
    }

    /**
     * 发送 SSE 事件，失败仅记录（客户端可能已断开）。
     */
    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.warn("SSE 事件发送失败（客户端可能已断开）: name={}", name);
        }
    }

    /**
     * 引用来源序列化。
     */
    private String toJson(List<ReferenceVO> references) {
        try {
            return OBJECT_MAPPER.writeValueAsString(references);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
