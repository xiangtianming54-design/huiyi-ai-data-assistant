package com.huiyi.assistant.service;

import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.ResultCode;
import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.context.WorkspaceContextHolder;
import com.huiyi.assistant.observability.LlmCallRecorder;
import com.huiyi.assistant.observability.ObservabilityContextHolder;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import com.huiyi.assistant.quota.StreamUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM 服务：OpenAI 兼容 API 的非流式与流式补全，统一异常包装。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class LlmService {

    /** 流式输出整体超时 */
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(30);

    private final ChatClient chatClient;
    private final LlmCallRecorder llmCallRecorder;
    private final ObservabilityMetrics observabilityMetrics;

    public LlmService(OpenAiChatModel openAiChatModel, LlmCallRecorder llmCallRecorder,
                      ObservabilityMetrics observabilityMetrics) {
        this.chatClient = ChatClient.builder(openAiChatModel).build();
        this.llmCallRecorder = llmCallRecorder;
        this.observabilityMetrics = observabilityMetrics;
    }

    /**
     * 非流式补全。
     *
     * @param prompt 完整提示词
     * @return 模型输出文本
     */
    public String chat(String prompt) {
        long start = System.nanoTime();
        String purpose = ObservabilityContextHolder.stepType() == null ? "llm" : ObservabilityContextHolder.stepType();
        try {
            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
            int promptTokens = tokens(response, true);
            int completionTokens = tokens(response, false);
            int latency = (int) Duration.ofNanos(System.nanoTime() - start).toMillis();
            llmCallRecorder.record(purpose, "SUCCESS", promptTokens, completionTokens, latency, null);
            observabilityMetrics.llm(true, promptTokens, completionTokens, Duration.ofMillis(latency));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return "";
            }
            String text = response.getResult().getOutput().getText();
            return text == null ? "" : text;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            int latency = (int) Duration.ofNanos(System.nanoTime() - start).toMillis();
            llmCallRecorder.record(purpose, "FAILED", 0, 0, latency, e.getMessage());
            observabilityMetrics.llm(false, 0, 0, Duration.ofMillis(latency));
            log.error("LLM 非流式调用失败: {}", e.getMessage());
            throw new BizException(ResultCode.LLM_SERVICE_ERROR);
        }
    }

    /**
     * 流式补全：返回 token 流，带整体超时与异常包装。
     *
     * @param prompt 完整提示词
     * @return token 流
     */
    public Flux<String> chatStream(String prompt) {
        long start = System.nanoTime();
        String purpose = ObservabilityContextHolder.stepType() == null ? "llm_stream" : ObservabilityContextHolder.stepType();
        String runId = ObservabilityContextHolder.runId();
        String requestId = ObservabilityContextHolder.requestId();
        WorkspaceContext workspace = WorkspaceContextHolder.get();
        AtomicInteger promptTokens = new AtomicInteger();
        AtomicInteger completionTokens = new AtomicInteger();
        AtomicBoolean sawUsage = new AtomicBoolean();
        return chatClient.prompt(prompt)
                .stream()
                .chatResponse()
                .timeout(STREAM_TIMEOUT)
                .doOnNext(response -> {
                    int[] usage = StreamUsage.from(usageOf(response));
                    if (usage != null) {
                        promptTokens.set(usage[0]);
                        completionTokens.set(usage[1]);
                        sawUsage.set(true);
                    }
                })
                .map(LlmService::chunkText)
                .filter(StringUtils::hasLength)
                .doOnComplete(() -> withRequestContext(runId, requestId, workspace, () -> {
                    int latency = (int) Duration.ofNanos(System.nanoTime() - start).toMillis();
                    int recordedPrompt = 0;
                    int recordedCompletion = 0;
                    if (sawUsage.get()) {
                        recordedPrompt = promptTokens.get();
                        recordedCompletion = completionTokens.get();
                    } else {
                        log.warn("流式用量缺失，本段记 0 purpose={}", purpose);
                    }
                    llmCallRecorder.record(purpose, "SUCCESS", recordedPrompt, recordedCompletion, latency, null);
                    observabilityMetrics.llm(true, recordedPrompt, recordedCompletion, Duration.ofMillis(latency));
                }))
                .doOnError(e -> withRequestContext(runId, requestId, workspace, () -> {
                    int latency = (int) Duration.ofNanos(System.nanoTime() - start).toMillis();
                    llmCallRecorder.record(purpose, "FAILED", 0, 0, latency, e.getMessage());
                    observabilityMetrics.llm(false, 0, 0, Duration.ofMillis(latency));
                    log.error("LLM 流式调用失败: {}", e.getMessage());
                }))
                .onErrorMap(e -> new BizException(ResultCode.LLM_SERVICE_ERROR));
    }

    /** 流式信号在池线程上，记账前把请求线程捕获的 runId、requestId、用户上下文放回 holder，用完即还原池线程原值。 */
    private static void withRequestContext(String runId, String requestId, WorkspaceContext workspace,
                                           Runnable action) {
        String previousRunId = ObservabilityContextHolder.runId();
        String previousRequestId = ObservabilityContextHolder.requestId();
        WorkspaceContext previousWorkspace = WorkspaceContextHolder.get();
        ObservabilityContextHolder.setRunId(runId);
        ObservabilityContextHolder.setRequestId(requestId);
        setWorkspace(workspace);
        try {
            action.run();
        } finally {
            ObservabilityContextHolder.setRunId(previousRunId);
            ObservabilityContextHolder.setRequestId(previousRequestId);
            setWorkspace(previousWorkspace);
        }
    }

    private static void setWorkspace(WorkspaceContext context) {
        if (context == null) {
            WorkspaceContextHolder.clear();
        } else {
            WorkspaceContextHolder.set(context);
        }
    }

    private static Usage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getUsage();
    }

    /** 与 Spring AI {@code content()} 相同：没有文本时是空串，空串随后被滤掉。 */
    private static String chunkText(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private int tokens(ChatResponse response, boolean prompt) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return 0;
        }
        Usage usage = response.getMetadata().getUsage();
        Integer value = prompt ? usage.getPromptTokens() : usage.getCompletionTokens();
        return value == null ? 0 : value;
    }
}
