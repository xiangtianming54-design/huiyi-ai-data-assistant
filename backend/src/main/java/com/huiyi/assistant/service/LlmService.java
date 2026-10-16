package com.huiyi.assistant.service;

import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.ResultCode;
import com.huiyi.assistant.observability.LlmCallRecorder;
import com.huiyi.assistant.observability.ObservabilityContextHolder;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;

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
        return chatClient.prompt(prompt)
                .stream()
                .content()
                .timeout(STREAM_TIMEOUT)
                .doOnComplete(() -> {
                    int latency = (int) Duration.ofNanos(System.nanoTime() - start).toMillis();
                    llmCallRecorder.record(purpose, "SUCCESS", 0, 0, latency, null);
                    observabilityMetrics.llm(true, 0, 0, Duration.ofMillis(latency));
                })
                .doOnError(e -> {
                    int latency = (int) Duration.ofNanos(System.nanoTime() - start).toMillis();
                    llmCallRecorder.record(purpose, "FAILED", 0, 0, latency, e.getMessage());
                    observabilityMetrics.llm(false, 0, 0, Duration.ofMillis(latency));
                    log.error("LLM 流式调用失败: {}", e.getMessage());
                })
                .onErrorMap(e -> new BizException(ResultCode.LLM_SERVICE_ERROR));
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
