package com.huiyi.assistant.observability;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.huiyi.assistant.mapper.AssistantRunMapper;
import com.huiyi.assistant.mapper.AssistantRunStepMapper;
import com.huiyi.assistant.mapper.LlmCallMapper;
import com.huiyi.assistant.model.entity.AssistantRun;
import com.huiyi.assistant.quota.RunTokenSum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Mock
    AssistantRunMapper runMapper;

    @Mock
    AssistantRunStepMapper stepMapper;

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

    @Test
    void finishRecordsZeroWhenSumThrows() {
        when(llmCallMapper.sumByRun("run_task7")).thenThrow(new RuntimeException("db"));

        assertDoesNotThrow(() -> service.finish("SUCCEEDED", "data_query", null, System.nanoTime()));

        verify(runRecorder).finish(eq("SUCCEEDED"), eq("data_query"), isNull(), eq(0), eq(0), any(), isNull());
    }

    @Test
    void finishUpdatesCapturedRunWhenHolderIsEmpty() {
        ObservabilityContextHolder.clear();
        when(llmCallMapper.sumByRun("run_captured")).thenReturn(new RunTokenSum(3, 4));
        AssistantRunRecorder recorder = new AssistantRunRecorder(runMapper, stepMapper, new ObservabilityProperties());
        RequestTraceService traced = new RequestTraceService(recorder, llmCallMapper);

        traced.finish("run_captured", "SUCCEEDED", "knowledge_qa", null, System.nanoTime());

        ArgumentCaptor<AssistantRun> patch = ArgumentCaptor.forClass(AssistantRun.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AssistantRun>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(runMapper).update(patch.capture(), wrapper.capture());
        assertEquals(3, patch.getValue().getPromptTokens());
        assertEquals(4, patch.getValue().getCompletionTokens());
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AssistantRun.class);
        AbstractWrapper<AssistantRun, ?, ?> where = (AbstractWrapper<AssistantRun, ?, ?>) wrapper.getValue();
        where.getSqlSegment();
        assertTrue(where.getParamNameValuePairs().containsValue("run_captured"));
        assertNull(ObservabilityContextHolder.runId());
    }
}
