package com.huiyi.assistant.observability;

import com.huiyi.assistant.mapper.LlmCallMapper;
import com.huiyi.assistant.quota.RunTokenSum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Service
public class RequestTraceService {

    private final AssistantRunRecorder runRecorder;
    private final LlmCallMapper llmCallMapper;

    public RequestTraceService(AssistantRunRecorder runRecorder, LlmCallMapper llmCallMapper) {
        this.runRecorder = runRecorder;
        this.llmCallMapper = llmCallMapper;
    }

    public String start(String entrypoint, String question, String sessionId) {
        return runRecorder.start(entrypoint, question, sessionId);
    }

    public <T> T timed(String stepType, Supplier<T> action) {
        ObservabilityContextHolder.setStepType(stepType);
        long start = System.nanoTime();
        try {
            T result = action.get();
            runRecorder.step(stepType, "SUCCESS", millis(start), null);
            return result;
        } catch (RuntimeException e) {
            runRecorder.step(stepType, "FAILED", millis(start), e.getMessage());
            throw e;
        } finally {
            ObservabilityContextHolder.clearStepType();
        }
    }

    public void step(String stepType, String status, long startNanos, String error) {
        runRecorder.step(stepType, status, millis(startNanos), error);
    }

    public void stepTool(String toolName, String status, long startNanos, String error) {
        runRecorder.step(stepTypeFor(toolName), toolName, status, millis(startNanos), error);
    }

    static String stepTypeFor(String toolName) {
        if (toolName == null) {
            return null;
        }
        return switch (toolName) {
            case "knowledge.search" -> "rag";
            case "sql.generate" -> "sql_generate";
            case "sql.validate" -> "sql_validate";
            case "sql.execute_readonly" -> "sql_execute";
            case "data.profile" -> "data_profile";
            default -> toolName;
        };
    }

    public void finish(String status, String intent, String error, long startNanos) {
        finish(ObservabilityContextHolder.runId(), status, intent, error, startNanos, null);
    }

    public void finish(String status, String intent, String error, long startNanos, String errorCode) {
        finish(ObservabilityContextHolder.runId(), status, intent, error, startNanos, errorCode);
    }

    public void finish(String runId, String status, String intent, String error, long startNanos) {
        finish(runId, status, intent, error, startNanos, null);
    }

    public void finish(String runId, String status, String intent, String error, long startNanos, String errorCode) {
        RunTokenSum sum = sumOrZero(runId);
        String previous = ObservabilityContextHolder.runId();
        if (runId != null) {
            ObservabilityContextHolder.setRunId(runId);
        }
        try {
            runRecorder.finish(status, intent, error, sum.promptTokens(), sum.completionTokens(),
                    millis(startNanos), errorCode);
        } finally {
            if (runId != null) {
                ObservabilityContextHolder.setRunId(previous);
            }
        }
    }

    private RunTokenSum sumOrZero(String runId) {
        if (runId == null) {
            return new RunTokenSum(0, 0);
        }
        try {
            RunTokenSum sum = llmCallMapper.sumByRun(runId);
            return sum == null ? new RunTokenSum(0, 0) : sum;
        } catch (RuntimeException e) {
            log.warn("llm_calls sum by run failed runId={}", runId, e);
            return new RunTokenSum(0, 0);
        }
    }

    public static int millis(long startNanos) {
        return (int) Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
