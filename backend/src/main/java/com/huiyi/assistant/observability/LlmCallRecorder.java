package com.huiyi.assistant.observability;

import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.context.WorkspaceContextHolder;
import com.huiyi.assistant.mapper.LlmCallMapper;
import com.huiyi.assistant.model.entity.LlmCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
public class LlmCallRecorder {

    private final LlmCallMapper llmCallMapper;
    private final ObservabilityProperties properties;
    private final String model;

    public LlmCallRecorder(LlmCallMapper llmCallMapper, ObservabilityProperties properties,
                           @Value("${llm.model:}") String model) {
        this.llmCallMapper = llmCallMapper;
        this.properties = properties;
        this.model = model;
    }

    public void record(String purpose, String status, int promptTokens, int completionTokens, int latencyMs,
                       String errorMessage) {
        if (!properties.isEnabled()) {
            return;
        }
        WorkspaceContext workspace = WorkspaceContextHolder.get();
        LlmCall call = new LlmCall();
        call.setRequestId(ObservabilityContextHolder.requestId() == null ? "unknown" : ObservabilityContextHolder.requestId());
        call.setRunId(ObservabilityContextHolder.runId());
        if (workspace != null) {
            call.setWorkspaceId(workspace.workspaceId());
            call.setUserId(workspace.userId());
        }
        String resolvedPurpose = purpose == null ? ObservabilityContextHolder.stepType() : purpose;
        call.setPurpose(resolvedPurpose == null ? "llm" : resolvedPurpose);
        call.setProvider("openai-compatible");
        call.setModel(model);
        call.setPromptTemplateCode(call.getPurpose());
        call.setPromptTokens(promptTokens);
        call.setCompletionTokens(completionTokens);
        call.setTotalTokens(promptTokens + completionTokens);
        call.setCostAmount(properties.isLlmCostEnabled() && properties.getLlmCostDefault() != null
                ? properties.getLlmCostDefault() : BigDecimal.ZERO);
        call.setLatencyMs(latencyMs);
        call.setStatus(status);
        call.setErrorMessage(errorMessage);
        call.setCreatedAt(LocalDateTime.now());
        try {
            llmCallMapper.insert(call);
        } catch (RuntimeException e) {
            log.warn("llm_calls insert failed purpose={}", call.getPurpose(), e);
        }
    }
}
