package com.huiyi.assistant.observability;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.context.WorkspaceContextHolder;
import com.huiyi.assistant.mapper.AssistantRunMapper;
import com.huiyi.assistant.mapper.AssistantRunStepMapper;
import com.huiyi.assistant.model.entity.AssistantRun;
import com.huiyi.assistant.model.entity.AssistantRunStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AssistantRunRecorder {

    private final AssistantRunMapper runMapper;
    private final AssistantRunStepMapper stepMapper;
    private final ObservabilityProperties properties;
    private final InheritableThreadLocal<AtomicInteger> stepSeq = new InheritableThreadLocal<>() {
        @Override
        protected AtomicInteger initialValue() {
            return new AtomicInteger();
        }
    };

    public AssistantRunRecorder(AssistantRunMapper runMapper, AssistantRunStepMapper stepMapper,
                                ObservabilityProperties properties) {
        this.runMapper = runMapper;
        this.stepMapper = stepMapper;
        this.properties = properties;
    }

    public String start(String entrypoint, String question, String sessionId) {
        String runId = ObservabilityIds.runId();
        ObservabilityContextHolder.setRunId(runId);
        stepSeq.get().set(0);
        if (!properties.isEnabled()) {
            return runId;
        }
        WorkspaceContext workspace = WorkspaceContextHolder.get();
        if (workspace == null) {
            return runId;
        }
        AssistantRun run = new AssistantRun();
        run.setRunId(runId);
        run.setRequestId(ObservabilityContextHolder.requestId());
        run.setSessionId(sessionId);
        run.setWorkspaceId(workspace.workspaceId());
        run.setUserId(workspace.userId());
        run.setEntrypoint(entrypoint);
        run.setQuestion(question == null ? "" : question);
        run.setStatus("RUNNING");
        run.setPromptTokens(0);
        run.setCompletionTokens(0);
        run.setTotalTokens(0);
        run.setCostAmount(properties.getLlmCostDefault() == null ? BigDecimal.ZERO : properties.getLlmCostDefault());
        run.setStartedAt(LocalDateTime.now());
        run.setCreatedAt(LocalDateTime.now());
        try {
            runMapper.insert(run);
        } catch (RuntimeException e) {
            log.warn("assistant_runs insert failed runId={}", runId, e);
        }
        ObservabilityContextHolder.setWorkspace(workspace.workspaceId(), workspace.userId());
        return runId;
    }

    public void step(String stepType, String status, Integer latencyMs, String errorMessage) {
        step(stepType, status, latencyMs, null, null, 0, 0, errorMessage);
    }

    public void step(String stepType, String status, Integer latencyMs, String provider, String model,
                     int promptTokens, int completionTokens, String errorMessage) {
        if (!properties.isEnabled() || !properties.isTraceStepsEnabled()) {
            return;
        }
        String runId = ObservabilityContextHolder.runId();
        if (runId == null) {
            return;
        }
        int next = stepSeq.get().incrementAndGet();
        if (next > properties.getTraceMaxSteps()) {
            return;
        }
        AssistantRunStep step = new AssistantRunStep();
        step.setRunId(runId);
        step.setStepNo(next);
        step.setStepType(stepType);
        step.setStatus(status);
        step.setProvider(provider);
        step.setModel(model);
        step.setPromptTokens(promptTokens);
        step.setCompletionTokens(completionTokens);
        step.setTotalTokens(promptTokens + completionTokens);
        step.setLatencyMs(latencyMs);
        step.setErrorMessage(trim(errorMessage, 1000));
        step.setCreatedAt(LocalDateTime.now());
        try {
            stepMapper.insert(step);
        } catch (RuntimeException e) {
            log.warn("assistant_run_steps insert failed runId={} step={}", runId, stepType, e);
        }
    }

    public void finish(String status, String intent, String errorMessage, int promptTokens, int completionTokens,
                       Integer totalLatencyMs) {
        String runId = ObservabilityContextHolder.runId();
        if (!properties.isEnabled() || runId == null) {
            stepSeq.remove();
            return;
        }
        try {
            AssistantRun patch = new AssistantRun();
            patch.setStatus(status);
            patch.setIntent(intent);
            patch.setErrorMessage(trim(errorMessage, 1000));
            patch.setPromptTokens(promptTokens);
            patch.setCompletionTokens(completionTokens);
            patch.setTotalTokens(promptTokens + completionTokens);
            patch.setTotalLatencyMs(totalLatencyMs);
            patch.setFinishedAt(LocalDateTime.now());
            runMapper.update(patch, new LambdaUpdateWrapper<AssistantRun>().eq(AssistantRun::getRunId, runId));
        } catch (RuntimeException e) {
            log.warn("assistant_runs finish failed runId={}", runId, e);
        } finally {
            stepSeq.remove();
        }
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
