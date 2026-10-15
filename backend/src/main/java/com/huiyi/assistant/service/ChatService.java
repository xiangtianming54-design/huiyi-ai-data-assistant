package com.huiyi.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.assistant.model.dto.ChatRequestDTO;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import com.huiyi.assistant.model.vo.ChatResponseVO;
import com.huiyi.assistant.model.vo.ReferenceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

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

    /** 数据查询意图占位话术（P5 接入 Text-to-SQL 后移除） */
    private static final String DATA_QUERY_PLACEHOLDER =
            "数据查询能力建设中（P5 阶段上线），当前您可以先问我业务口径类问题，例如「什么是有效订单？」。";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IntentService intentService;

    private final RagService ragService;

    private final LlmService llmService;

    private final ChatSessionService chatSessionService;

    public ChatService(IntentService intentService, RagService ragService,
                       LlmService llmService, ChatSessionService chatSessionService) {
        this.intentService = intentService;
        this.ragService = ragService;
        this.llmService = llmService;
        this.chatSessionService = chatSessionService;
    }

    /**
     * 非流式问答。
     *
     * @param request 问答请求
     * @return 问答响应
     */
    public ChatResponseVO chat(ChatRequestDTO request) {
        String sessionId = chatSessionService.resolveSessionId(request.getSessionId());
        IntentTypeEnum intent = intentService.detect(request.getQuestion());
        chatSessionService.saveUserMessage(sessionId, request.getQuestion(), intent);

        String answer;
        List<ReferenceVO> references;
        if (intent == IntentTypeEnum.DATA_QUERY) {
            answer = DATA_QUERY_PLACEHOLDER;
            references = List.of();
        } else {
            RagService.RagAnswer ragAnswer = ragService.answer(request.getQuestion(), llmService);
            answer = ragAnswer.answer();
            references = ragAnswer.references();
        }

        chatSessionService.saveAssistantMessage(sessionId, answer, intent, null, references);
        return ChatResponseVO.builder()
                .sessionId(sessionId)
                .answer(answer)
                .intent(intent.getCode())
                .references(references)
                .build();
    }

    /**
     * 流式问答：SSE 逐 token 推送，结束时推送引用来源，完成后会话落库。
     *
     * @param request 问答请求
     * @return SSE 发射器
     */
    public SseEmitter chatStream(ChatRequestDTO request) {
        String sessionId = chatSessionService.resolveSessionId(request.getSessionId());
        IntentTypeEnum intent = intentService.detect(request.getQuestion());
        chatSessionService.saveUserMessage(sessionId, request.getQuestion(), intent);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        Flux<String> tokenFlux;
        List<ReferenceVO> references;
        if (intent == IntentTypeEnum.DATA_QUERY) {
            tokenFlux = Flux.just(DATA_QUERY_PLACEHOLDER);
            references = List.of();
        } else {
            RagService.RagContext context = ragService.prepare(request.getQuestion());
            references = context.references();
            tokenFlux = ragService.stream(context, llmService);
        }

        StringBuilder fullAnswer = new StringBuilder();
        List<ReferenceVO> finalReferences = references;
        tokenFlux.subscribe(
                token -> {
                    fullAnswer.append(token);
                    sendEvent(emitter, "token", token);
                },
                error -> {
                    // LLM 异常 / 超时：推送兜底文案，中断内容落库（4.8）
                    log.warn("流式问答中断: {}", error.getMessage());
                    String partial = fullAnswer.toString();
                    sendEvent(emitter, "error", "AI 服务暂不可用，请稍后重试。");
                    emitter.complete();
                    chatSessionService.saveAssistantMessage(sessionId,
                            partial.isEmpty() ? "AI 服务暂不可用，请稍后重试。" : partial,
                            intent, null, finalReferences);
                },
                () -> {
                    sendEvent(emitter, "references", toJson(finalReferences));
                    emitter.complete();
                    chatSessionService.saveAssistantMessage(sessionId, fullAnswer.toString(),
                            intent, null, finalReferences);
                });
        return emitter;
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
