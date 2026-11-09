package com.huiyi.assistant.observability;

import com.huiyi.assistant.mapper.LlmCallMapper;
import com.huiyi.assistant.quota.RunTokenSum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 运行结束时把该 run 下 SUCCESS 调用的 token 合计写回，不再写死 0。
 */
@ExtendWith(MockitoExtension.class)
class RequestTraceServiceTest {

    @Mock
    AssistantRunRecorder runRecorder;

    @Mock
    LlmCallMapper llmCallMapper;

    RequestTraceService service;

    @BeforeEach
    void setUp() {
        service = new RequestTraceService(runRecorder, llmCallMapper);
        ObservabilityContextHolder.setRunId("run_task7");
    }

    @AfterEach
    void tearDown() {
        ObservabilityContextHolder.clear();
    }

    @Test
    void finishPassesMappedPromptAndCompletion() {
        when(llmCallMapper.sumByRun("run_task7")).thenReturn(new RunTokenSum(3, 4));

        service.finish("SUCCEEDED", "data_query", null, System.nanoTime());

        verify(runRecorder).finish(eq("SUCCEEDED"), eq("data_query"), isNull(), eq(3), eq(4), any(), isNull());
    }
}
