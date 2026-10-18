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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
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

    @Test
    void askPersistsUserMessageBeforeRunner() {
        when(chatSessionService.resolveSessionId(any())).thenReturn("s1");
        when(agentRunner.runAsk(any())).thenReturn(List.of(sampleKnowledgeResult()));
        ChatRequestDTO request = new ChatRequestDTO();
        request.setQuestion("什么是有效订单？");
        chatService.ask(request);
        InOrder order = inOrder(chatSessionService, agentRunner);
        order.verify(chatSessionService).saveUserMessage(eq("s1"), eq("什么是有效订单？"), nullable(IntentTypeEnum.class));
        order.verify(agentRunner).runAsk(any(AgentRequest.class));
    }

    @Test
    void askKeepsUserMessageWhenRunnerThrows() {
        when(chatSessionService.resolveSessionId(any())).thenReturn("s1");
        when(agentRunner.runAsk(any())).thenThrow(new RuntimeException("boom"));
        ChatRequestDTO request = new ChatRequestDTO();
        request.setQuestion("什么是有效订单？");
        assertThrows(RuntimeException.class, () -> chatService.ask(request));
        verify(chatSessionService).saveUserMessage(eq("s1"), eq("什么是有效订单？"), nullable(IntentTypeEnum.class));
    }

    @Test
    void chatStreamFinishesRunAndSavesUserWhenRunnerThrows() {
        when(chatSessionService.resolveSessionId(any())).thenReturn("s1");
        when(agentRunner.run(any())).thenThrow(new RuntimeException("boom"));
        ChatRequestDTO request = new ChatRequestDTO();
        request.setQuestion("什么是有效订单？");
        assertThrows(RuntimeException.class, () -> chatService.chatStream(request));
        verify(chatSessionService).saveUserMessage(eq("s1"), eq("什么是有效订单？"), nullable(IntentTypeEnum.class));
        verify(requestTraceService).finish(eq("FAILED"), nullable(String.class), anyString(), anyLong());
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
