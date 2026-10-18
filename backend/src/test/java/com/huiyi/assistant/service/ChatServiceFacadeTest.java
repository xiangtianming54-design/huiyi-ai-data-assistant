package com.huiyi.assistant.service;

import com.huiyi.assistant.agent.AgentRequest;
import com.huiyi.assistant.agent.AgentResult;
import com.huiyi.assistant.agent.AgentRunner;
import com.huiyi.assistant.model.dto.ChatRequestDTO;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import com.huiyi.assistant.model.vo.ReferenceVO;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceFacadeTest {

    @Mock AgentRunner agentRunner;
    @Mock IntentService intentService;
    @Mock RagService ragService;
    @Mock LlmService llmService;
    @Mock ChatSessionService chatSessionService;
    @Mock QuestionDecomposeService questionDecomposeService;
    @Mock RequestTraceService requestTraceService;
    @Mock ObservabilityMetrics observabilityMetrics;

    @InjectMocks ChatService chatService;

    @Test
    void askDelegatesToRunnerNotExecuteQuery() {
        when(chatSessionService.resolveSessionId(any())).thenReturn("s1");
        when(agentRunner.runAsk(any())).thenReturn(List.of(sampleKnowledgeResult()));
        ChatRequestDTO request = new ChatRequestDTO();
        request.setQuestion("什么是有效订单？");
        chatService.ask(request);
        verify(agentRunner).runAsk(any(AgentRequest.class));
    }

    @Test
    void chatStreamEventNamesStayTokenOnly() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/huiyi/assistant/service/ChatService.java"),
                StandardCharsets.UTF_8);
        assertFalse(src.contains("sendEvent(emitter, \"step\""));
        assertTrue(src.contains("sendEvent(emitter, \"token\""));
    }

    @Test
    void chatServiceSourceDoesNotCallExecuteQuery() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/huiyi/assistant/service/ChatService.java"),
                StandardCharsets.UTF_8);
        assertFalse(src.contains("executeQuery("));
    }

    private static AgentResult sampleKnowledgeResult() {
        return new AgentResult(
                new IntentService.IntentDecision(IntentTypeEnum.KNOWLEDGE_QA, IntentService.Layer.RULE, 1.0),
                "有效订单是已支付订单",
                null,
                List.of(new ReferenceVO("glossary.md", 0.9)),
                null,
                null);
    }
}
