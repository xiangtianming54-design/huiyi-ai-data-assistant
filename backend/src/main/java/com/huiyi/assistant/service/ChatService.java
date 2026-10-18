package com.huiyi.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.assistant.agent.AgentDeadline;
import com.huiyi.assistant.agent.AgentRequest;
import com.huiyi.assistant.agent.AgentResult;
import com.huiyi.assistant.agent.AgentRunner;
import com.huiyi.assistant.agent.OutputGuard;
import com.huiyi.assistant.agent.ToolEventListener;
import com.huiyi.assistant.model.dto.ChatRequestDTO;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 问答门面：会话与 SSE 推字；编排委托 {@link AgentRunner}。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class ChatService {

    private static final long SSE_TIMEOUT_MS = Duration.ofSeconds(90).toMillis();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final OutputGuard OUTPUT_GUARD = new OutputGuard();

    private final AgentRunner agentRunner;
    private final IntentService intentService;
    private final RagService ragService;
    private final LlmService llmService;
    private final ChatSessionService chatSessionService;
    private final QuestionDecomposeService questionDecomposeService;
    private final RequestTraceService requestTraceService;
    private final ObservabilityMetrics observabilityMetrics;

    public ChatService(AgentRunner agentRunner, IntentService intentService, RagService ragService,
                       LlmService llmService, ChatSessionService chatSessionService,
                       QuestionDecomposeService questionDecomposeService,
                       RequestTraceService requestTraceService, ObservabilityMetrics observabilityMetrics) {
        this.agentRunner = agentRunner;
        this.intentService = intentService;
        this.ragService = ragService;
        this.llmService = llmService;
        this.chatSessionService = chatSessionService;
        this.questionDecomposeService = questionDecomposeService;
        this.requestTraceService = requestTraceService;
        this.observabilityMetrics = observabilityMetrics;
    }

    public IntentTypeEnum detectIntent(String question) {
        return intentService.detect(question);
    }

    public IntentResponseVO precheck(String question) {
        if (isSuspectedCompound(question)) {
            List<String> subQuestions = questionDecomposeService.decompose(question);
            if (subQuestions.size() > 1) {
                return new IntentResponseVO("compound", subQuestions);
            }
        }
        return new IntentResponseVO(intentService.detect(question).getCode(), null);
    }

    private boolean isSuspectedCompound(String question) {
        if (question == null) {
            return false;
        }
        return question.chars().filter(c -> c == '？' || c == '?' || c == '；' || c == ';').count() >= 2;
    }

    public ChatAskResponseVO ask(ChatRequestDTO request) {
        observabilityMetrics.chatRequest();
        long start = System.nanoTime();
        requestTraceService.start("chat_ask", request.getQuestion(), request.getSessionId());
        try {
            String sessionId = resolveScopedSession(request.getSessionId());
            chatSessionService.saveUserMessage(sessionId, request.getQuestion(), null);
            List<AgentResult> results = agentRunner.runAsk(
                    new AgentRequest("chat_ask", request.getQuestion(), sessionId));
            List<ChatPartVO> parts = results.stream()
                    .map(result -> toPart(result, request.getQuestion()))
                    .toList();
            for (AgentResult result : results) {
                persistAssistant(sessionId, result);
            }
            requestTraceService.finish("SUCCEEDED", null, null, start);
            observabilityMetrics.chatSuccess(Duration.ofNanos(System.nanoTime() - start), null);
            return ChatAskResponseVO.builder().sessionId(sessionId).parts(parts).build();
        } catch (RuntimeException e) {
            requestTraceService.finish("FAILED", null, e.getMessage(), start);
            observabilityMetrics.chatFailed(Duration.ofNanos(System.nanoTime() - start));
            throw e;
        }
    }

    public ChatResponseVO chat(ChatRequestDTO request) {
        observabilityMetrics.chatRequest();
        long start = System.nanoTime();
        requestTraceService.start("chat", request.getQuestion(), request.getSessionId());
        try {
            String sessionId = resolveScopedSession(request.getSessionId());
            chatSessionService.saveUserMessage(sessionId, request.getQuestion(), null);
            AgentResult result = agentRunner.run(new AgentRequest("chat", request.getQuestion(), sessionId));
            IntentTypeEnum intent = result.decision().intent();
            persistAssistant(sessionId, result);
            requestTraceService.finish("SUCCEEDED", intent.getCode(), null, start);
            observabilityMetrics.chatSuccess(Duration.ofNanos(System.nanoTime() - start), null);
            return ChatResponseVO.builder()
                    .sessionId(sessionId)
                    .answer(OUTPUT_GUARD.sanitize(result.answer()))
                    .intent(intent.getCode())
                    .references(result.references() == null ? List.of() : result.references())
                    .build();
        } catch (RuntimeException e) {
            requestTraceService.finish("FAILED", null, e.getMessage(), start);
            observabilityMetrics.chatFailed(Duration.ofNanos(System.nanoTime() - start));
            throw e;
        }
    }

    public SseEmitter chatStream(ChatRequestDTO request) {
        observabilityMetrics.chatRequest();
        long start = System.nanoTime();
        requestTraceService.start("chat_stream", request.getQuestion(), request.getSessionId());
        String sessionId = resolveScopedSession(request.getSessionId());
        chatSessionService.saveUserMessage(sessionId, request.getQuestion(), null);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        sendEvent(emitter, "session", sessionId);
        AgentResult result;
        try {
            result = agentRunner.run(new AgentRequest("chat_stream", request.getQuestion(), sessionId),
                    toolSseListener(emitter), AgentDeadline.fromNow(SSE_TIMEOUT_MS));
        } catch (RuntimeException e) {
            requestTraceService.finish("FAILED", null, e.getMessage(), start);
            observabilityMetrics.chatFailed(Duration.ofNanos(System.nanoTime() - start));
            throw e;
        }
        IntentService.IntentDecision decision = result.decision();
        IntentTypeEnum intent = decision.intent();
        if (result.errorCode() != null) {
            sendEvent(emitter, "error", OUTPUT_GUARD.sanitize(AgentRunner.LIMIT_USER_MESSAGE));
            emitter.complete();
            chatSessionService.saveAssistantMessage(sessionId, OUTPUT_GUARD.sanitize(AgentRunner.LIMIT_USER_MESSAGE),
                    intent, null, List.of(),
                    decision.layer().name().toLowerCase(), decision.score());
            requestTraceService.finish("FAILED", intent.getCode(), result.errorCode(), start, result.errorCode());
            observabilityMetrics.chatFailed(Duration.ofNanos(System.nanoTime() - start));
            return emitter;
        }

        List<ReferenceVO> references = result.references() == null ? List.of() : result.references();
        String sqlText = result.sql();
        Flux<String> tokenFlux;
        if (result.ragContext() != null && result.ragContext().needLlm()) {
            tokenFlux = ragService.stream(result.ragContext(), llmService);
        } else {
            tokenFlux = Flux.just(result.answer() == null ? "" : result.answer());
        }

        StringBuilder fullAnswer = new StringBuilder();
        AtomicBoolean firstToken = new AtomicBoolean(true);
        AtomicLong firstTokenAt = new AtomicLong();
        tokenFlux.subscribe(
                token -> {
                    if (firstToken.compareAndSet(true, false)) {
                        firstTokenAt.set(System.nanoTime());
                    }
                    fullAnswer.append(token);
                    sendEvent(emitter, "token", OUTPUT_GUARD.sanitize(token));
                },
                error -> {
                    log.warn("流式问答中断: {}", error.getMessage());
                    String partial = OUTPUT_GUARD.sanitize(fullAnswer.toString());
                    sendEvent(emitter, "error", OUTPUT_GUARD.sanitize("AI 服务暂不可用，请稍后重试。"));
                    emitter.complete();
                    chatSessionService.saveAssistantMessage(sessionId,
                            (partial == null || partial.isEmpty())
                                    ? OUTPUT_GUARD.sanitize("AI 服务暂不可用，请稍后重试。")
                                    : partial,
                            intent, null, references,
                            decision.layer().name().toLowerCase(), decision.score());
                    requestTraceService.finish("FAILED", intent.getCode(), error.getMessage(), start);
                    observabilityMetrics.chatFailed(Duration.ofNanos(System.nanoTime() - start));
                },
                () -> {
                    sendEvent(emitter, "references", toJson(references));
                    emitter.complete();
                    chatSessionService.saveAssistantMessage(sessionId, OUTPUT_GUARD.sanitize(fullAnswer.toString()),
                            intent, sqlText, references,
                            decision.layer().name().toLowerCase(), decision.score());
                    requestTraceService.finish("SUCCEEDED", intent.getCode(), null, start);
                    Duration first = firstTokenAt.get() == 0 ? null : Duration.ofNanos(firstTokenAt.get() - start);
                    observabilityMetrics.chatSuccess(Duration.ofNanos(System.nanoTime() - start), first);
                });
        return emitter;
    }

    private ChatPartVO toPart(AgentResult result, String originalQuestion) {
        IntentTypeEnum intent = result.decision().intent();
        return ChatPartVO.builder()
                .question(originalQuestion)
                .intent(intent.getCode())
                .answer(OUTPUT_GUARD.sanitize(result.answer()))
                .references(result.references() == null ? List.of() : result.references())
                .analysis(result.analysis())
                .build();
    }

    private void persistAssistant(String sessionId, AgentResult result) {
        IntentService.IntentDecision decision = result.decision();
        chatSessionService.saveAssistantMessage(sessionId, OUTPUT_GUARD.sanitize(result.answer()), decision.intent(),
                result.sql(), result.references() == null ? List.of() : result.references(),
                decision.layer().name().toLowerCase(), decision.score());
    }

    private String resolveScopedSession(String sessionId) {
        com.huiyi.assistant.context.WorkspaceContext context = com.huiyi.assistant.context.WorkspaceContextHolder.get();
        if (context == null) {
            return chatSessionService.resolveSessionId(sessionId);
        }
        return chatSessionService.ensureSession(context.workspaceId(), context.userId(), sessionId);
    }

    private ToolEventListener toolSseListener(SseEmitter emitter) {
        return new ToolEventListener() {
            @Override
            public void onStarted(String toolName) {
                sendEvent(emitter, "tool.started", toolEventJson(toolName, null));
            }

            @Override
            public void onCompleted(String toolName, String status) {
                sendEvent(emitter, "tool.completed", toolEventJson(toolName, status));
            }

            @Override
            public void onFailed(String toolName, String status) {
                sendEvent(emitter, "tool.failed", toolEventJson(toolName, status));
            }
        };
    }

    private String toolEventJson(String toolName, String status) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("tool", toolName);
        if (status != null) {
            payload.put("status", status);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"tool\":\"" + toolName + "\"}";
        }
    }

    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.warn("SSE 事件发送失败（客户端可能已断开）: name={}", name);
        }
    }

    private String toJson(List<ReferenceVO> references) {
        try {
            return OBJECT_MAPPER.writeValueAsString(references);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
