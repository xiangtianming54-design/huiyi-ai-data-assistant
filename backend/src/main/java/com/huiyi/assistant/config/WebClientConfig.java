package com.huiyi.assistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 配置：访问 Python Sidecar 服务。
 *
 * @author huiyi
 */
@Configuration
public class WebClientConfig {

    /**
     * Python Sidecar 专用 WebClient。
     *
     * @param baseUrl Python 服务地址，来自 python-service.url 配置
     * @param builder Spring 托管的 WebClient.Builder
     * @return WebClient 实例
     */
    @Bean
    public WebClient pythonServiceWebClient(@Value("${python-service.url}") String baseUrl,
                                            WebClient.Builder builder) {
        return builder.baseUrl(baseUrl).build();
    }
}
