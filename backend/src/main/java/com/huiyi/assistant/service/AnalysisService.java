package com.huiyi.assistant.service;

import com.huiyi.assistant.agent.AgentRequest;
import com.huiyi.assistant.agent.AgentResult;
import com.huiyi.assistant.agent.AgentRunner;
import com.huiyi.assistant.model.dto.AnalysisRequestDTO;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import com.huiyi.assistant.model.vo.AnalysisResultVO;
import com.huiyi.assistant.observability.ObservabilityContextHolder;
import com.huiyi.assistant.observability.RequestTraceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据分析门面：会话落库；编排委托 {@link AgentRunner}。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class AnalysisService {

    private final AgentRunner agentRunner;
    private final ChatSessionService chatSessionService;
    private final RequestTraceService requestTraceService;

    public AnalysisService(AgentRunner agentRunner, ChatSessionService chatSessionService,
                           RequestTraceService requestTraceService) {
        this.agentRunner = agentRunner;
        this.chatSessionService = chatSessionService;
        this.requestTraceService = requestTraceService;
    }

    public AnalysisResultVO query(AnalysisRequestDTO request) {
        boolean ownsRun = ObservabilityContextHolder.runId() == null;
        long start = System.nanoTime();
        if (ownsRun) {
            requestTraceService.start("analysis_query", request.getQuestion(), request.getSessionId());
        }
        try {
            String sessionId = resolveScopedSession(request.getSessionId());
            chatSessionService.saveUserMessage(sessionId, request.getQuestion(), IntentTypeEnum.DATA_QUERY);
            AgentResult agentResult = agentRunner.run(
                    new AgentRequest("analysis_query", request.getQuestion(), sessionId));
            AnalysisResultVO result = agentResult.analysis() == null
                    ? AnalysisResultVO.builder().answer(agentResult.answer()).sql(agentResult.sql()).build()
                    : agentResult.analysis();
            result.setSessionId(sessionId);
            chatSessionService.saveAssistantMessage(sessionId, result.getAnswer(),
                    IntentTypeEnum.DATA_QUERY, result.getSql(), List.of());
            if (ownsRun) {
                requestTraceService.finish("SUCCEEDED", IntentTypeEnum.DATA_QUERY.getCode(), null, start);
            }
            return result;
        } catch (RuntimeException e) {
            if (ownsRun) {
                requestTraceService.finish("FAILED", IntentTypeEnum.DATA_QUERY.getCode(), e.getMessage(), start);
            }
            throw e;
        }
    }

    private String resolveScopedSession(String sessionId) {
        com.huiyi.assistant.context.WorkspaceContext context = com.huiyi.assistant.context.WorkspaceContextHolder.get();
        if (context == null) {
            return chatSessionService.resolveSessionId(sessionId);
        }
        return chatSessionService.ensureSession(context.workspaceId(), context.userId(), sessionId);
    }
}
