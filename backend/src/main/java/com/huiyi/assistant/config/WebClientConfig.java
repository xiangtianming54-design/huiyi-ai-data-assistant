package com.huiyi.assistant.config;

import com.huiyi.assistant.observability.ObservabilityContextHolder;
import com.huiyi.assistant.observability.RequestIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient pythonServiceWebClient(@Value("${python-service.url}") String baseUrl,
                                            WebClient.Builder builder) {
        return builder.baseUrl(baseUrl).filter(observabilityHeaders()).build();
    }

    public static ExchangeFilterFunction observabilityHeaders() {
        return (request, next) -> {
            ClientRequest.Builder mutated = ClientRequest.from(request);
            String requestId = ObservabilityContextHolder.requestId();
            if (requestId != null) {
                mutated.header(RequestIdFilter.REQUEST_ID_HEADER, requestId);
            }
            String runId = ObservabilityContextHolder.runId();
            if (runId != null) {
                mutated.header(RequestIdFilter.RUN_ID_HEADER, runId);
            }
            return next.exchange(mutated.build());
        };
    }
}
