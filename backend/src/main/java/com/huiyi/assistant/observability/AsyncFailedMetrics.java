package com.huiyi.assistant.observability;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huiyi.assistant.knowledge.AsyncTaskService;
import com.huiyi.assistant.mapper.AsyncTaskMapper;
import com.huiyi.assistant.model.entity.AsyncTask;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AsyncFailedMetrics {

    public AsyncFailedMetrics(ObservabilityProperties properties, MeterRegistry registry, AsyncTaskMapper mapper) {
        if (!properties.isMetricsEnabled()) {
            return;
        }
        Gauge.builder("huiyi.async.failed", () -> mapper.selectCount(new QueryWrapper<AsyncTask>()
                        .eq("status", AsyncTaskService.STATUS_FAILED)))
                .register(registry);
    }
}
