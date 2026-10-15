package com.huiyi.assistant.config;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * LLM 配置：OpenAI 兼容 API 的 ChatModel。
 * 手工装配以获得明确的超时（30s 读）与重试（共 2 次尝试）控制。
 *
 * @author huiyi
 */
@Configuration
public class LlmConfig {

    /** 连接超时 */
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    /** 读取超时（非流式补全整体上限） */
    private static final int READ_TIMEOUT_MS = 30_000;

    @Bean
    public OpenAiChatModel openAiChatModel(@Value("${llm.base-url}") String baseUrl,
                                           @Value("${llm.api-key}") String apiKey,
                                           @Value("${llm.model}") String model) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                // base-url 已含 /v1（与 Python embedding 共用同一配置），补全路径不再重复 /v1
                .completionsPath("/chat/completions")
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();

        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.2)
                .build();

        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(2)
                .exponentialBackoff(Duration.ofMillis(500).toMillis(), 2, Duration.ofSeconds(5).toMillis())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(defaultOptions)
                .retryTemplate(retryTemplate)
                .build();
    }
}
