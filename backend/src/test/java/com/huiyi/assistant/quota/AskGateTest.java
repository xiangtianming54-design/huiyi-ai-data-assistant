package com.huiyi.assistant.quota;

import com.huiyi.assistant.agent.AgentRunner;
import com.huiyi.assistant.api.v1.AnalysisV1Controller;
import com.huiyi.assistant.api.v1.ChatV1Controller;
import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.model.dto.AnalysisRequestDTO;
import com.huiyi.assistant.model.dto.ChatRequestDTO;
import com.huiyi.assistant.model.entity.ChatMessage;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import com.huiyi.assistant.model.vo.IntentResponseVO;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import com.huiyi.assistant.observability.RequestTraceService;
import com.huiyi.assistant.security.AuthUser;
import com.huiyi.assistant.service.AnalysisService;
import com.huiyi.assistant.service.ChatService;
import com.huiyi.assistant.service.ChatSessionService;
import com.huiyi.assistant.service.IntentService;
import com.huiyi.assistant.service.LlmService;
import com.huiyi.assistant.service.QueryRewriteService;
import com.huiyi.assistant.service.QuestionDecomposeService;
import com.huiyi.assistant.service.RagService;
import com.huiyi.assistant.workspace.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 开跑前闸门：先准入，再用近期原文检查额度。拒绝时不进入问答或分析。
 *
 * @author huiyi
 */
@ExtendWith(MockitoExtension.class)
class AskGateTest {

    private static final long USER_ID = 7L;
    private static final long WORKSPACE_ID = 3L;
    private static final String QUESTION = "今天卖了多少";
    private static final String SESSION_ID = "s";
    private static final String OLD_TICKET = "old-ticket";
    private static final String NEW_TICKET = "new-ticket";

    @Mock
    private AskAdmission admission;

    @Mock
    private TokenQuotaService quota;

    @Mock
    private ChatSessionService sessions;

    @Test
    void beginRejectionSkipsQuota() {
        when(admission.begin(USER_ID)).thenThrow(new BizException(4291, "请求过于频繁，请稍后再试"));
        AskGate gate = new AskGate(admission, quota, sessions);

        BizException ex = assertThrows(BizException.class,
                () -> gate.enter(USER_ID, QUESTION, SESSION_ID, null, true));

        assertEquals(4291, ex.getCode());
        verify(quota, never()).assertCanStart(anyLong(), any(), any());
        verifyNoInteractions(sessions);
    }

    @Test
    void quotaRejectionKeeps4292() {
        BizException denied = new BizException(4292, "今日 Token 额度不足，请提额后再问。");
        when(admission.begin(USER_ID)).thenReturn(NEW_TICKET);
        when(sessions.getRecentMessages(SESSION_ID, QueryRewriteService.HISTORY_LIMIT)).thenReturn(List.of());
        doThrow(denied).when(quota).assertCanStart(anyLong(), any(), any());
        AskGate gate = new AskGate(admission, quota, sessions);

        BizException ex = assertThrows(BizException.class,
                () -> gate.enter(USER_ID, QUESTION, SESSION_ID, null, true));

        assertSame(denied, ex);
        assertEquals(4292, ex.getCode());
    }

    @Test
    void issuingReturnsNewTicketAndLoadsHistoryLimit() {
        assertEquals(20, QueryRewriteService.HISTORY_LIMIT);
        when(admission.begin(USER_ID)).thenReturn(NEW_TICKET);
        when(sessions.getRecentMessages(SESSION_ID, QueryRewriteService.HISTORY_LIMIT))
                .thenReturn(List.of(message("earlier"), message("later")));
        AskGate gate = new AskGate(admission, quota, sessions);

        String ticket = gate.enter(USER_ID, QUESTION, SESSION_ID, null, true);

        assertEquals(NEW_TICKET, ticket);
        verify(admission).begin(USER_ID);
        verify(admission, never()).follow(anyLong(), any());
        verify(sessions).getRecentMessages(SESSION_ID, QueryRewriteService.HISTORY_LIMIT);
        verify(quota).assertCanStart(USER_ID, QUESTION, List.of("earlier", "later"));
        verify(quota, never()).estimate(any(), any());
    }

    @Test
    void emptySessionIdUsesNoRecentText() {
        when(admission.begin(USER_ID)).thenReturn(NEW_TICKET);
        AskGate gate = new AskGate(admission, quota, sessions);

        assertEquals(NEW_TICKET, gate.enter(USER_ID, QUESTION, null, null, true));
        gate.enter(USER_ID, QUESTION, "", OLD_TICKET, false);

        verify(admission).follow(USER_ID, OLD_TICKET);
        verify(quota, times(2)).assertCanStart(USER_ID, QUESTION, List.of());
        verifyNoInteractions(sessions);
    }

    @Test
    void followUsesPresentedTicketThenRecentText() {
        when(sessions.getRecentMessages(SESSION_ID, QueryRewriteService.HISTORY_LIMIT))
                .thenReturn(List.of(message("prev")));
        AskGate gate = new AskGate(admission, quota, sessions);

        gate.enter(USER_ID, QUESTION, SESSION_ID, OLD_TICKET, false);

        verify(admission).follow(USER_ID, OLD_TICKET);
        verify(admission, never()).begin(anyLong());
        verify(quota).assertCanStart(USER_ID, QUESTION, List.of("prev"));
        verify(quota, never()).estimate(any(), any());
    }

    @Test
    void chatEndpointsDoNotRunWhenGateRejects() {
        AskGate gate = mock(AskGate.class);
        ChatService chatService = mock(ChatService.class);
        AuditService auditService = mock(AuditService.class);
        WorkspaceService workspaceService = mock(WorkspaceService.class);
        AuthUser user = new AuthUser(USER_ID, "ada", 1);
        when(workspaceService.requireMember(user, WORKSPACE_ID)).thenReturn(context());
        BizException denied = new BizException(4292, "今日 Token 额度不足，请提额后再问。");
        doThrow(denied).when(gate).enter(anyLong(), any(), any(), any(), anyBoolean());
        ChatV1Controller controller = new ChatV1Controller(
                workspaceService, chatService, mock(ChatSessionService.class), auditService, gate);
        ChatRequestDTO request = chatRequest();

        assertSame(denied, assertThrows(BizException.class, () -> controller.intent(user, WORKSPACE_ID, request)));
        assertSame(denied, assertThrows(BizException.class, () -> controller.chat(user, WORKSPACE_ID, request)));
        assertSame(denied, assertThrows(BizException.class, () -> controller.ask(user, WORKSPACE_ID, request)));
        assertSame(denied, assertThrows(BizException.class, () -> controller.stream(user, WORKSPACE_ID, request)));

        verify(chatService, never()).precheck(any(), any());
        verify(chatService, never()).chat(any());
        verify(chatService, never()).ask(any());
        verify(chatService, never()).chatStream(any());
        verifyNoInteractions(auditService);
        verify(gate).enter(USER_ID, QUESTION, SESSION_ID, OLD_TICKET, true);
        verify(gate, times(3)).enter(USER_ID, QUESTION, SESSION_ID, OLD_TICKET, false);
    }

    @Test
    void analysisDoesNotRunWhenGateRejects() {
        AskGate gate = mock(AskGate.class);
        AnalysisService analysisService = mock(AnalysisService.class);
        AuditService auditService = mock(AuditService.class);
        WorkspaceService workspaceService = mock(WorkspaceService.class);
        AuthUser user = new AuthUser(USER_ID, "ada", 1);
        when(workspaceService.requireMember(user, WORKSPACE_ID)).thenReturn(context());
        BizException denied = new BizException(4292, "今日 Token 额度不足，请提额后再问。");
        doThrow(denied).when(gate).enter(anyLong(), any(), any(), any(), anyBoolean());
        AnalysisV1Controller controller = new AnalysisV1Controller(
                workspaceService, analysisService, auditService, gate);
        AnalysisRequestDTO request = new AnalysisRequestDTO();
        request.setQuestion(QUESTION);
        request.setSessionId(SESSION_ID);
        request.setAdmissionTicket(OLD_TICKET);

        assertSame(denied, assertThrows(BizException.class, () -> controller.query(user, WORKSPACE_ID, request)));

        verify(analysisService, never()).query(any());
        verifyNoInteractions(auditService);
        verify(gate).enter(USER_ID, QUESTION, SESSION_ID, OLD_TICKET, false);
    }

    @Test
    void intentPassesIssuedTicketToPrecheck() {
        AskGate gate = mock(AskGate.class);
        ChatService chatService = mock(ChatService.class);
        WorkspaceService workspaceService = mock(WorkspaceService.class);
        AuthUser user = new AuthUser(USER_ID, "ada", 1);
        when(workspaceService.requireMember(user, WORKSPACE_ID)).thenReturn(context());
        when(gate.enter(USER_ID, QUESTION, SESSION_ID, OLD_TICKET, true)).thenReturn(NEW_TICKET);
        when(chatService.precheck(QUESTION, NEW_TICKET))
                .thenReturn(new IntentResponseVO("knowledge_qa", null, NEW_TICKET));
        ChatV1Controller controller = new ChatV1Controller(
                workspaceService, chatService, mock(ChatSessionService.class), mock(AuditService.class), gate);

        Result<IntentResponseVO> result = controller.intent(user, WORKSPACE_ID, chatRequest());

        assertEquals(NEW_TICKET, result.getData().getAdmissionTicket());
    }

    @Test
    void precheckPutsTicketOnBothResponses() {
        IntentService intentService = mock(IntentService.class);
        QuestionDecomposeService decompose = mock(QuestionDecomposeService.class);
        when(intentService.detect("plain")).thenReturn(IntentTypeEnum.KNOWLEDGE_QA);
        when(decompose.decompose("甲？乙？")).thenReturn(List.of("甲", "乙"));
        ChatService chatService = new ChatService(
                mock(AgentRunner.class), intentService, mock(RagService.class), mock(LlmService.class),
                mock(ChatSessionService.class), decompose, mock(RequestTraceService.class),
                mock(ObservabilityMetrics.class));

        IntentResponseVO plain = chatService.precheck("plain", NEW_TICKET);
        IntentResponseVO compound = chatService.precheck("甲？乙？", NEW_TICKET);

        assertEquals("knowledge_qa", plain.getIntent());
        assertEquals(NEW_TICKET, plain.getAdmissionTicket());
        assertEquals("compound", compound.getIntent());
        assertEquals(List.of("甲", "乙"), compound.getQuestions());
        assertEquals(NEW_TICKET, compound.getAdmissionTicket());
    }

    private static ChatRequestDTO chatRequest() {
        ChatRequestDTO request = new ChatRequestDTO();
        request.setQuestion(QUESTION);
        request.setSessionId(SESSION_ID);
        request.setAdmissionTicket(OLD_TICKET);
        return request;
    }

    private static WorkspaceContext context() {
        return new WorkspaceContext(USER_ID, WORKSPACE_ID, 9L, "MEMBER", "ACTIVE", "ACTIVE");
    }

    private static ChatMessage message(String content) {
        ChatMessage message = new ChatMessage();
        message.setContent(content);
        return message;
    }
}
