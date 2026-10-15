package com.huiyi.assistant.service;

import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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

    public LlmService(OpenAiChatModel openAiChatModel) {
        this.chatClient = ChatClient.builder(openAiChatModel).build();
    }

    /**
     * 非流式补全。
     *
     * @param prompt 完整提示词
     * @return 模型输出文本
     */
    public String chat(String prompt) {
        try {
            return chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
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
        return chatClient.prompt(prompt)
                .stream()
                .content()
                .timeout(STREAM_TIMEOUT)
                .doOnError(e -> log.error("LLM 流式调用失败: {}", e.getMessage()))
                .onErrorMap(e -> new BizException(ResultCode.LLM_SERVICE_ERROR));
    }
}
