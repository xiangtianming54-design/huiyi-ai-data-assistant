package com.huiyi.assistant.service;

import com.huiyi.assistant.agent.AgentDeadline;
import com.huiyi.assistant.agent.AgentRequest;
import com.huiyi.assistant.agent.AgentResult;
import com.huiyi.assistant.agent.AgentRunner;
import com.huiyi.assistant.agent.ToolEventListener;
import com.huiyi.assistant.mapper.LlmCallMapper;
import com.huiyi.assistant.model.dto.ChatRequestDTO;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import com.huiyi.assistant.observability.AssistantRunRecorder;
import com.huiyi.assistant.observability.ObservabilityContextHolder;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import com.huiyi.assistant.observability.RequestTraceService;
import com.huiyi.assistant.quota.RunTokenSum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流式完成回调不在请求线程上，合计仍要用请求线程上拿到的 runId。
 */
@ExtendWith(MockitoExtension.class)
class ChatStreamRunTotalTest {

    @Mock AgentRunner agentRunner;
    @Mock IntentService intentService;
    @Mock RagService ragService;
    @Mock LlmService llmService;
    @Mock ChatSessionService chatSessionService;
    @Mock QuestionDecomposeService questionDecomposeService;
    @Mock ObservabilityMetrics observabilityMetrics;
    @Mock AssistantRunRecorder runRecorder;
    @Mock LlmCallMapper llmCallMapper;

    @AfterEach
    void tearDown() {
        ObservabilityContextHolder.clear();
    }

    @Test
    void streamFinishRollsTokensWhenCallbackHasNoThreadLocalRunId() {
        when(runRecorder.start(eq("chat_stream"), any(), any())).thenAnswer(invocation -> {
            ObservabilityContextHolder.setRunId("run_stream");
            return "run_stream";
        });
        lenient().when(llmCallMapper.sumByRun("run_stream")).thenReturn(new RunTokenSum(3, 4));
        when(chatSessionService.resolveSessionId(any())).thenReturn("s1");
        when(agentRunner.run(any(AgentRequest.class), any(ToolEventListener.class), any(AgentDeadline.class)))
                .thenReturn(streamingResult());
        when(ragService.stream(any(), eq(llmService))).thenReturn(
                Flux.just("token").doOnComplete(ObservabilityContextHolder::clear));

        RequestTraceService traces = new RequestTraceService(runRecorder, llmCallMapper);
        ChatService chatService = new ChatService(agentRunner, intentService, ragService, llmService,
                chatSessionService, questionDecomposeService, traces, observabilityMetrics);
        ChatRequestDTO request = new ChatRequestDTO();
        request.setQuestion("什么是有效订单？");

        chatService.chatStream(request);

        assertNull(ObservabilityContextHolder.runId());
        verify(llmCallMapper).sumByRun("run_stream");
        verify(runRecorder).finish(eq("SUCCEEDED"), eq("knowledge_qa"), isNull(), eq(3), eq(4), any(), isNull());
    }

    private static AgentResult streamingResult() {
        return new AgentResult(
                new IntentService.IntentDecision(IntentTypeEnum.KNOWLEDGE_QA, IntentService.Layer.RULE, 1.0),
                null,
                null,
                List.of(),
                null,
                new RagService.RagContext(null, "prompt", List.of()),
                null);
    }
}
