package com.huiyi.assistant.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ObservabilityMetrics {

    private final ObservabilityProperties properties;
    private final MeterRegistry registry;

    public ObservabilityMetrics(ObservabilityProperties properties, MeterRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    public void chatRequest() {
        increment("chat_requests");
    }

    public void chatSuccess(Duration latency, Duration firstToken) {
        increment("chat_success");
        timer("chat_total_latency_ms", latency);
        if (firstToken != null) {
            timer("chat_first_token_latency_ms", firstToken);
        }
    }

    public void chatFailed(Duration latency) {
        increment("chat_failed");
        timer("chat_total_latency_ms", latency);
    }

    public void ragSearch(Duration latency, boolean hit, boolean failed) {
        increment("rag_search");
        timer("rag_search_latency_ms", latency);
        if (failed) {
            increment("rag_search_failed");
        } else if (!hit) {
            increment("rag_no_hit");
        }
    }

    public void llm(boolean success, int promptTokens, int completionTokens, Duration latency) {
        increment("llm_request");
        if (success) {
            increment("llm_success");
        } else {
            increment("llm_failed");
        }
        add("llm_prompt_tokens", promptTokens);
        add("llm_completion_tokens", completionTokens);
        add("llm_total_tokens", promptTokens + completionTokens);
        timer("llm_latency_ms", latency);
    }

    public void sqlGenerate(boolean success) {
        increment("sql_generate");
        if (!success) {
            increment("sql_generate_failed");
        }
    }

    public void sqlValidate(boolean passed) {
        increment("sql_validate");
        if (passed) {
            increment("sql_validate_success");
        } else {
            increment("sql_validate_rejected");
        }
    }

    public void sqlExecute(boolean success, boolean timeout, boolean rowsLimited, Duration latency) {
        increment("sql_execute");
        if (success) {
            increment("sql_execute_success");
        } else {
            increment("sql_execute_failed");
        }
        if (timeout) {
            increment("sql_timeout");
        }
        if (rowsLimited) {
            increment("sql_rows_limited");
        }
        timer("sql_execute_latency_ms", latency);
    }

    public void python(boolean success, boolean parseFailed, boolean embeddingFailed, Duration latency) {
        increment("python_request");
        if (success) {
            increment("python_success");
        } else {
            increment("python_failed");
        }
        if (parseFailed) {
            increment("document_parse_failed");
        }
        if (embeddingFailed) {
            increment("embedding_failed");
        }
        timer("python_request_latency_ms", latency);
    }

    private void increment(String name) {
        if (!properties.isMetricsEnabled()) {
            return;
        }
        Counter.builder(name).register(registry).increment();
    }

    private void add(String name, int amount) {
        if (!properties.isMetricsEnabled() || amount <= 0) {
            return;
        }
        Counter.builder(name).register(registry).increment(amount);
    }

    private void timer(String name, Duration latency) {
        if (!properties.isMetricsEnabled() || latency == null) {
            return;
        }
        Timer.builder(name).register(registry).record(latency);
    }
}
