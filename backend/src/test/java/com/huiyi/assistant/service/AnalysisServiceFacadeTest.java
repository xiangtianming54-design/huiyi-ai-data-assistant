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

import java.util.List;

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
    void queryDelegatesToRunner() {
        when(chatSessionService.resolveSessionId(any())).thenReturn("s1");
        AnalysisResultVO analysis = AnalysisResultVO.builder().answer("ok").sql("select 1").build();
        when(agentRunner.run(any())).thenReturn(new AgentResult(
                new IntentService.IntentDecision(IntentTypeEnum.DATA_QUERY, IntentService.Layer.RULE, 1.0),
                "ok", "select 1", List.of(), analysis, null));
        AnalysisRequestDTO request = new AnalysisRequestDTO();
        request.setQuestion("上个月销售额？");
        analysisService.query(request);
        verify(agentRunner).run(any(AgentRequest.class));
    }
}
