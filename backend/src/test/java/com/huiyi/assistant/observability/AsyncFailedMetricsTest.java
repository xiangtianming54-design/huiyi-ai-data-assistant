package com.huiyi.assistant.observability;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.huiyi.assistant.mapper.AsyncTaskMapper;
import com.huiyi.assistant.model.entity.AsyncTask;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncFailedMetricsTest {

    @Mock
    AsyncTaskMapper mapper;

    @Test
    void gaugeReadsCurrentFailedCountOnEveryRead() {
        when(mapper.selectCount(argThat(AsyncFailedMetricsTest::countsFailedStatus))).thenReturn(2L);
        ObservabilityProperties properties = new ObservabilityProperties();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new AsyncFailedMetrics(properties, registry, mapper);

        assertEquals(2.0, registry.get("huiyi.async.failed").gauge().value());

        when(mapper.selectCount(argThat(AsyncFailedMetricsTest::countsFailedStatus))).thenReturn(5L);
        assertEquals(5.0, registry.get("huiyi.async.failed").gauge().value());
    }

    @Test
    void doesNotRegisterGaugeWhenMetricsDisabled() {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setMetricsEnabled(false);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new AsyncFailedMetrics(properties, registry, mapper);

        assertNull(registry.find("huiyi.async.failed").gauge());
    }

    private static boolean countsFailedStatus(Wrapper<AsyncTask> wrapper) {
        if (!(wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper)) {
            return false;
        }
        String sql = abstractWrapper.getSqlSegment();
        return sql != null
                && sql.contains("status")
                && abstractWrapper.getParamNameValuePairs().containsValue("FAILED");
    }
}
