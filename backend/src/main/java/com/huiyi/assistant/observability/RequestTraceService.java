package com.huiyi.assistant.observability;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
public class RequestTraceService {

    private final AssistantRunRecorder runRecorder;

    public RequestTraceService(AssistantRunRecorder runRecorder) {
        this.runRecorder = runRecorder;
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

    public void finish(String status, String intent, String error, long startNanos) {
        runRecorder.finish(status, intent, error, 0, 0, millis(startNanos));
    }

    public static int millis(long startNanos) {
        return (int) Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
