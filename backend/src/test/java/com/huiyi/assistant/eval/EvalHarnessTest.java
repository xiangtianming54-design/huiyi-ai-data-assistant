package com.huiyi.assistant.eval;

import com.huiyi.assistant.agent.AgentRunner;
import com.huiyi.assistant.agent.RouterPlanner;
import com.huiyi.assistant.agent.ToolExecutor;
import com.huiyi.assistant.common.util.PromptTemplateLoader;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import com.huiyi.assistant.observability.RequestTraceService;
import com.huiyi.assistant.service.IntentService;
import com.huiyi.assistant.service.LlmService;
import com.huiyi.assistant.service.QueryRewriteService;
import com.huiyi.assistant.service.QuestionDecomposeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalHarnessTest {

    @Mock QueryRewriteService queryRewriteService;
    @Mock IntentService intentService;
    @Mock QuestionDecomposeService questionDecomposeService;
    @Mock RouterPlanner routerPlanner;
    @Mock ToolExecutor toolExecutor;
    @Mock RequestTraceService requestTraceService;
    @Mock LlmService llmService;
    @Mock PromptTemplateLoader promptTemplateLoader;
    @Mock ObservabilityMetrics observabilityMetrics;
    @Mock ExecutorService chatExecutor;

    @InjectMocks AgentRunner runner;

    @BeforeEach
    void setUp() {
        lenient().when(requestTraceService.timed(anyString(), any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(1);
            return action.get();
        });
    }

    @AfterEach
    void tearDown() {
        com.huiyi.assistant.context.WorkspaceContextHolder.clear();
    }

    @Test
    void deadlineUsesLimitCopy() {
        when(queryRewriteService.rewrite(any(), any())).thenReturn("q");
        when(intentService.detectWithDetail("q")).thenReturn(
                new IntentService.IntentDecision(IntentTypeEnum.DATA_QUERY, IntentService.Layer.RULE, 1.0));
        when(routerPlanner.toolsFor(IntentTypeEnum.DATA_QUERY)).thenReturn(List.of("sql.generate"));
        EvalCase cse = new EvalCase();
        cse.setId("limit");
        cse.setEntrypoint("analysis_query");
        cse.setQuestion("q");
        cse.setSessionId("s1");
        cse.stubs().setDeadlineExpired(true);
        EvalActual actual = new EvalHarness().run(cse, runner);
        assertEquals(AgentRunner.LIMIT_USER_MESSAGE, actual.answer());
        assertEquals("AGENT_LIMIT_EXCEEDED", actual.errorCode());
    }

    @Test
    void dirtyAnswerSkipsRunnerAndSanitizes() {
        EvalCase cse = new EvalCase();
        cse.setId("guard");
        cse.setEntrypoint("analysis_query");
        cse.setQuestion("q");
        cse.stubs().setDirtyAnswer("ok\nException in thread \"main\" boom");
        EvalActual actual = new EvalHarness().run(cse, runner);
        assertFalse(actual.answer().contains("Exception in thread"));
    }
}
