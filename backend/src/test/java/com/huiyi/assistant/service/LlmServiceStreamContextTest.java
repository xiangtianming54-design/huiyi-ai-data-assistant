package com.huiyi.assistant.service;

import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.context.WorkspaceContextHolder;
import com.huiyi.assistant.mapper.LlmCallMapper;
import com.huiyi.assistant.model.entity.LlmCall;
import com.huiyi.assistant.observability.LlmCallRecorder;
import com.huiyi.assistant.observability.ObservabilityContextHolder;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import com.huiyi.assistant.observability.ObservabilityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流式完成/失败回调在池线程上，记账仍要用请求线程捕获的用户与请求标识，且用完即还原。
 */
@ExtendWith(MockitoExtension.class)
class LlmServiceStreamContextTest {

    private static final WorkspaceContext REQUEST_CONTEXT =
            new WorkspaceContext(42L, 7L, 1L, "OWNER", "ACTIVE", "ACTIVE");

    @Mock OpenAiChatModel openAiChatModel;
    @Mock LlmCallMapper llmCallMapper;
    @Mock ObservabilityMetrics observabilityMetrics;

    @AfterEach
    void tearDown() {
        WorkspaceContextHolder.clear();
        ObservabilityContextHolder.clear();
    }

    @Test
    void streamSuccessBillsCapturedUserWhenCallbackThreadHasNoContext() {
        when(openAiChatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(responseWithUsage()).publishOn(Schedulers.boundedElastic()));
        LlmService service = service();
        AtomicReference<WorkspaceContext> workspaceBeforeRecord = new AtomicReference<>();
        AtomicReference<String> runIdBeforeRecord = new AtomicReference<>();
        AtomicReference<WorkspaceContext> workspaceAfterRecord = new AtomicReference<>();
        AtomicReference<String> runIdAfterRecord = new AtomicReference<>();

        requestThreadContext();
        service.chatStream("什么是有效订单？")
                .doOnNext(token -> {
                    workspaceBeforeRecord.compareAndSet(null, WorkspaceContextHolder.get());
                    runIdBeforeRecord.compareAndSet(null, ObservabilityContextHolder.runId());
                })
                .doOnComplete(() -> {
                    workspaceAfterRecord.set(WorkspaceContextHolder.get());
                    runIdAfterRecord.set(ObservabilityContextHolder.runId());
                })
                .blockLast();

        LlmCall inserted = insertedCall();
        assertEquals("SUCCESS", inserted.getStatus());
        assertEquals(42L, inserted.getUserId());
        assertEquals(7L, inserted.getWorkspaceId());
        assertEquals("req_stream", inserted.getRequestId());
        assertEquals("run_stream", inserted.getRunId());
        assertEquals(3, inserted.getPromptTokens());
        assertEquals(4, inserted.getCompletionTokens());
        assertNull(workspaceAfterRecord.get(), "池线程记账后必须还原，不能留下捕获的用户");
        assertEquals(runIdBeforeRecord.get(), runIdAfterRecord.get(), "池线程记账后必须还原其原有 runId");
    }

    @Test
    void streamFailureBillsCapturedUserWhenCallbackThreadHasNoContext() {
        when(openAiChatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.<ChatResponse>error(new IllegalStateException("llm down"))
                        .publishOn(Schedulers.boundedElastic()));
        LlmService service = service();
        AtomicReference<WorkspaceContext> workspaceAfterRecord = new AtomicReference<>();

        requestThreadContext();
        assertThrows(BizException.class, () -> service.chatStream("什么是有效订单？")
                .doOnError(e -> workspaceAfterRecord.set(WorkspaceContextHolder.get()))
                .blockLast());

        LlmCall inserted = insertedCall();
        assertEquals("FAILED", inserted.getStatus());
        assertEquals(42L, inserted.getUserId());
        assertEquals(7L, inserted.getWorkspaceId());
        assertEquals("req_stream", inserted.getRequestId());
        assertEquals("run_stream", inserted.getRunId());
        assertNull(workspaceAfterRecord.get(), "池线程记账后必须还原，不能留下捕获的用户");
    }

    private LlmService service() {
        LlmCallRecorder recorder = new LlmCallRecorder(llmCallMapper, new ObservabilityProperties(), "test-model");
        return new LlmService(openAiChatModel, recorder, observabilityMetrics);
    }

    private void requestThreadContext() {
        WorkspaceContextHolder.set(REQUEST_CONTEXT);
        ObservabilityContextHolder.setRequestId("req_stream");
        ObservabilityContextHolder.setRunId("run_stream");
    }

    private LlmCall insertedCall() {
        ArgumentCaptor<LlmCall> captor = ArgumentCaptor.forClass(LlmCall.class);
        verify(llmCallMapper).insert(captor.capture());
        return captor.getValue();
    }

    private static ChatResponse responseWithUsage() {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(3, 4))
                .build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage("有效订单是……"))), metadata);
    }
}
