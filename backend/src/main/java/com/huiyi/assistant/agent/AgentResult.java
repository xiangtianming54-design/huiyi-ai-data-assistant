package com.huiyi.assistant.agent;

import com.huiyi.assistant.model.vo.AnalysisResultVO;
import com.huiyi.assistant.model.vo.ReferenceVO;
import com.huiyi.assistant.service.IntentService;
import com.huiyi.assistant.service.RagService;

import java.util.List;

public record AgentResult(
        IntentService.IntentDecision decision,
        String answer,
        String sql,
        List<ReferenceVO> references,
        AnalysisResultVO analysis,
        RagService.RagContext ragContext,
        String errorCode
) {
}
