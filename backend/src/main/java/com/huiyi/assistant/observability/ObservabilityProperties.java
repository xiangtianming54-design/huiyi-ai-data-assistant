package com.huiyi.assistant.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "huiyi.observability")
public class ObservabilityProperties {

    private boolean enabled = true;
    private boolean metricsEnabled = true;
    private boolean llmCostEnabled = true;
    private BigDecimal llmCostDefault = BigDecimal.ZERO;
    private boolean traceStepsEnabled = true;
    private int traceMaxSteps = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public boolean isLlmCostEnabled() {
        return llmCostEnabled;
    }

    public void setLlmCostEnabled(boolean llmCostEnabled) {
        this.llmCostEnabled = llmCostEnabled;
    }

    public BigDecimal getLlmCostDefault() {
        return llmCostDefault;
    }

    public void setLlmCostDefault(BigDecimal llmCostDefault) {
        this.llmCostDefault = llmCostDefault;
    }

    public boolean isTraceStepsEnabled() {
        return traceStepsEnabled;
    }

    public void setTraceStepsEnabled(boolean traceStepsEnabled) {
        this.traceStepsEnabled = traceStepsEnabled;
    }

    public int getTraceMaxSteps() {
        return traceMaxSteps;
    }

    public void setTraceMaxSteps(int traceMaxSteps) {
        this.traceMaxSteps = traceMaxSteps;
    }
}
