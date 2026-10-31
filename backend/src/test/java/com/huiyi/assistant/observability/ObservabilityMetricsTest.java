package com.huiyi.assistant.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilityMetricsTest {

    @Test
    void recordsSuccessCounterAndValidationLatency() {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setMetricsEnabled(true);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilityMetrics metrics = new ObservabilityMetrics(properties, registry);

        metrics.sqlValidate(true, Duration.ofMillis(12));

        assertEquals(1.0d, registry.get("sql_validate").counter().count());
        assertEquals(1.0d, registry.get("sql_validate_success").counter().count());
        assertEquals(1L, registry.get("sql_validate_latency_ms").timer().count());
    }

    @Test
    void recordsRejectedCounter() {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setMetricsEnabled(true);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilityMetrics metrics = new ObservabilityMetrics(properties, registry);

        metrics.sqlValidate(false, Duration.ofMillis(4));

        assertEquals(1.0d, registry.get("sql_validate_rejected").counter().count());
    }

    @Test
    void doesNotRegisterMetersWhenMetricsDisabled() {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setMetricsEnabled(false);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilityMetrics metrics = new ObservabilityMetrics(properties, registry);

        metrics.sqlValidate(true, Duration.ofMillis(12));

        assertTrue(registry.getMeters().isEmpty());
    }
}
