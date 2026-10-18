package com.huiyi.assistant.service;

import com.huiyi.assistant.agent.AgentRequest;
import com.huiyi.assistant.agent.AgentResult;
import com.huiyi.assistant.agent.AgentRunner;
import com.huiyi.assistant.model.dto.AnalysisRequestDTO;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import com.huiyi.assistant.model.vo.AnalysisResultVO;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import com.huiyi.assistant.observability.RequestTraceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceFacadeTest {

    @Mock AgentRunner agentRunner;
    @Mock ChatSessionService chatSessionService;
    @Mock RequestTraceService requestTraceService;
    @Mock ObservabilityMetrics observabilityMetrics;

    @InjectMocks AnalysisService analysisService;

    @Test
    void queryJsonHasNoToolCalls() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/huiyi/assistant/service/AnalysisService.java"),
                StandardCharsets.UTF_8);
        assertFalse(src.contains("toolCalls"));
        assertFalse(src.contains("tool.started"));
    }

    @Test
    void queryDelegatesToRunner() {
        when(chatSessionService.resolveSessionId(any())).thenReturn("s1");
        AnalysisResultVO analysis = AnalysisResultVO.builder().answer("ok").sql("select 1").build();
        when(agentRunner.run(any())).thenReturn(new AgentResult(
                new IntentService.IntentDecision(IntentTypeEnum.DATA_QUERY, IntentService.Layer.RULE, 1.0),
                "ok", "select 1", List.of(), analysis, null, null));
        AnalysisRequestDTO request = new AnalysisRequestDTO();
        request.setQuestion("上个月销售额？");
        analysisService.query(request);
        verify(agentRunner).run(any(AgentRequest.class));
    }

    @Test
    void knowledgeResultHasEmptyRowsNotNull() {
        when(chatSessionService.resolveSessionId(any())).thenReturn("s1");
        when(agentRunner.run(any())).thenReturn(new AgentResult(
                new IntentService.IntentDecision(IntentTypeEnum.KNOWLEDGE_QA, IntentService.Layer.RULE, 1.0),
                "有效订单是已支付订单", null, List.of(), null, null, null));
        AnalysisRequestDTO request = new AnalysisRequestDTO();
        request.setQuestion("什么是有效订单？");
        AnalysisResultVO result = analysisService.query(request);
        assertNotNull(result.getRows());
        assertEquals(List.of(), result.getRows());
        assertEquals("有效订单是已支付订单", result.getAnswer());
    }
}
