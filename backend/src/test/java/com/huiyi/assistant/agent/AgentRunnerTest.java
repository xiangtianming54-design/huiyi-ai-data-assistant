package com.huiyi.assistant.agent;

import com.huiyi.assistant.common.result.ResultCode;
import com.huiyi.assistant.common.util.PromptTemplateLoader;
import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.context.WorkspaceContextHolder;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import com.huiyi.assistant.model.vo.ReferenceVO;
import com.huiyi.assistant.observability.ObservabilityMetrics;
import com.huiyi.assistant.observability.RequestTraceService;
import com.huiyi.assistant.service.IntentService;
import com.huiyi.assistant.service.LlmService;
import com.huiyi.assistant.service.QueryRewriteService;
import com.huiyi.assistant.service.QuestionDecomposeService;
import com.huiyi.assistant.service.RagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunnerTest {

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
        WorkspaceContextHolder.set(new WorkspaceContext(9L, 2L, 1L, "VIEWER", "ACTIVE", "ACTIVE"));
        lenient().when(requestTraceService.timed(anyString(), any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(1);
            return action.get();
        });
    }

    @AfterEach
    void tearDown() {
        WorkspaceContextHolder.clear();
    }

    @Test
    void schemaInvalidUsesUnknownToolCopyNotLimitCode() {
        when(toolExecutor.execute(eq("sql.validate"), any(), any()))
                .thenReturn(ToolResult.fail("schema_invalid"));
        ToolResult result = runner.invoke("sql.validate", Map.of("sql", "select 1"));
        assertEquals(AgentRunner.UNKNOWN_TOOL_USER_MESSAGE, result.error());
        verify(requestTraceService).stepTool(eq("sql.validate"), eq("REJECTED"), anyLong(), any());
    }

    @Test
    void deadlineStopsExecutor() {
        when(queryRewriteService.rewrite(any(), eq("q"))).thenReturn("q");
        when(intentService.detectWithDetail("q"))
                .thenReturn(new IntentService.IntentDecision(IntentTypeEnum.DATA_QUERY, IntentService.Layer.RULE, 1.0));
        when(routerPlanner.toolsFor(IntentTypeEnum.DATA_QUERY))
                .thenReturn(List.of("sql.generate"));
        AgentResult limited = runner.run(new AgentRequest("analysis_query", "q", "s1"),
                ToolEventListener.NOOP, AgentDeadline.alreadyExpired());
        assertEquals(AgentRunner.LIMIT_USER_MESSAGE, limited.answer());
        assertEquals("AGENT_LIMIT_EXCEEDED", limited.errorCode());
        verify(toolExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void unknownToolIsCaughtAndDoesNotUseRagFallback() {
        when(toolExecutor.execute(eq("sql.execute_write"), any(), any()))
                .thenThrow(new UnknownToolException("sql.execute_write"));
        ToolResult result = runner.invoke("sql.execute_write", Map.of());
        assertFalse(result.ok());
        assertEquals(AgentRunner.UNKNOWN_TOOL_USER_MESSAGE, result.error());
        assertNotEquals(RagService.NO_HIT_FALLBACK, result.error());
        verify(requestTraceService).stepTool(eq("sql.execute_write"), eq("FAILED"), anyLong(), any());
    }

    @Test
    void dataQueryInvokesFourToolsInOrder() {
        when(queryRewriteService.rewrite(any(), eq("q"))).thenReturn("q");
        when(intentService.detectWithDetail("q"))
                .thenReturn(new IntentService.IntentDecision(IntentTypeEnum.DATA_QUERY, IntentService.Layer.RULE, 1.0));
        when(routerPlanner.toolsFor(IntentTypeEnum.DATA_QUERY)).thenReturn(List.of(
                ToolNames.SQL_GENERATE, ToolNames.SQL_VALIDATE, ToolNames.SQL_EXECUTE_READONLY, ToolNames.DATA_PROFILE));
        when(toolExecutor.execute(eq(ToolNames.SQL_GENERATE), any(), any()))
                .thenReturn(ToolResult.ok(Map.of("sql", "select 1")));
        when(toolExecutor.execute(eq(ToolNames.SQL_VALIDATE), any(), any()))
                .thenReturn(ToolResult.ok(Map.of("valid", true, "sql", "select 1")));
        when(toolExecutor.execute(eq(ToolNames.SQL_EXECUTE_READONLY), any(), any()))
                .thenReturn(ToolResult.ok(Map.of("rows", List.of(Map.of("n", 1)))));
        when(toolExecutor.execute(eq(ToolNames.DATA_PROFILE), any(), any()))
                .thenReturn(ToolResult.ok(Map.of("chartType", "bar", "rowCount", 1)));
        when(promptTemplateLoader.render(anyString(), any())).thenReturn("p");
        when(llmService.chat("p")).thenReturn("总结");

        runner.run(new AgentRequest("analysis_query", "q", "s1"));

        InOrder order = inOrder(toolExecutor);
        order.verify(toolExecutor).execute(eq(ToolNames.SQL_GENERATE), any(), any());
        order.verify(toolExecutor).execute(eq(ToolNames.SQL_VALIDATE), any(), any());
        order.verify(toolExecutor).execute(eq(ToolNames.SQL_EXECUTE_READONLY), any(), any());
        order.verify(toolExecutor).execute(eq(ToolNames.DATA_PROFILE), any(), any());
    }

    @Test
    void knowledgeStreamLeavesRagContext() {
        when(queryRewriteService.rewrite(any(), eq("q"))).thenReturn("q");
        when(intentService.detectWithDetail("q"))
                .thenReturn(new IntentService.IntentDecision(IntentTypeEnum.KNOWLEDGE_QA, IntentService.Layer.RULE, 1.0));
        when(routerPlanner.toolsFor(IntentTypeEnum.KNOWLEDGE_QA)).thenReturn(List.of(ToolNames.KNOWLEDGE_SEARCH));
        Map<String, Object> searchData = new java.util.HashMap<>();
        searchData.put("fallbackAnswer", null);
        searchData.put("prompt", " rag prompt ");
        searchData.put("references", List.of(new ReferenceVO("a.md", 0.9)));
        when(toolExecutor.execute(eq(ToolNames.KNOWLEDGE_SEARCH), any(), any()))
                .thenReturn(ToolResult.ok(searchData));

        AgentResult result = runner.run(new AgentRequest("chat_stream", "q", "s1"));
        assertNull(result.answer());
        assertEquals(" rag prompt ", result.ragContext().prompt());
    }

    @Test
    void invokeIgnoresArgumentWorkspaceId() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("fallbackAnswer", "x");
        data.put("prompt", null);
        data.put("references", List.of());
        when(toolExecutor.execute(eq(ToolNames.KNOWLEDGE_SEARCH), any(), any()))
                .thenReturn(ToolResult.ok(data));
        runner.invoke(ToolNames.KNOWLEDGE_SEARCH, Map.of("workspaceId", 99L));
        ArgumentCaptor<ToolContext> captor = ArgumentCaptor.forClass(ToolContext.class);
        verify(toolExecutor).execute(eq(ToolNames.KNOWLEDGE_SEARCH), captor.capture(), any());
        assertEquals(2L, captor.getValue().workspaceId());
    }

    @Test
    void generateTimeoutUsesLlmOutageCopy() {
        stubDataQuery();
        when(toolExecutor.execute(eq(ToolNames.SQL_GENERATE), any(), any()))
                .thenReturn(ToolResult.fail("timeout"));
        AgentResult result = runner.run(new AgentRequest("analysis_query", "q", "s1"));
        assertEquals(ResultCode.LLM_SERVICE_ERROR.getMessage(), result.answer());
    }

    @Test
    void validateTimeoutUsesServiceCopyNotModificationRefusal() {
        stubDataQuery();
        when(toolExecutor.execute(eq(ToolNames.SQL_GENERATE), any(), any()))
                .thenReturn(ToolResult.ok(Map.of("sql", "select 1")));
        when(toolExecutor.execute(eq(ToolNames.SQL_VALIDATE), any(), any()))
                .thenReturn(ToolResult.fail("timeout"));
        AgentResult result = runner.run(new AgentRequest("analysis_query", "q", "s1"));
        assertEquals(ResultCode.PYTHON_SERVICE_UNAVAILABLE.getMessage(), result.answer());
    }

    @Test
    void executeTimeoutUsesSqlExecuteFailureCopy() {
        stubDataQuery();
        when(toolExecutor.execute(eq(ToolNames.SQL_GENERATE), any(), any()))
                .thenReturn(ToolResult.ok(Map.of("sql", "select 1")));
        when(toolExecutor.execute(eq(ToolNames.SQL_VALIDATE), any(), any()))
                .thenReturn(ToolResult.ok(Map.of("valid", true, "sql", "select 1")));
        when(toolExecutor.execute(eq(ToolNames.SQL_EXECUTE_READONLY), any(), any()))
                .thenReturn(ToolResult.fail("timeout"));
        AgentResult result = runner.run(new AgentRequest("analysis_query", "q", "s1"));
        assertEquals("SQL 执行失败，请调整问题后重试", result.answer());
    }

    @Test
    void rejectedSqlIsTracedRejectedNotSuccess() {
        stubDataQuery();
        when(toolExecutor.execute(eq(ToolNames.SQL_GENERATE), any(), any()))
                .thenReturn(ToolResult.ok(Map.of("sql", "select 1")));
        when(toolExecutor.execute(eq(ToolNames.SQL_VALIDATE), any(), any()))
                .thenReturn(ToolResult.ok(Map.of("valid", false, "reason", "表不在白名单")));
        runner.run(new AgentRequest("analysis_query", "q", "s1"));
        verify(requestTraceService).stepTool(eq(ToolNames.SQL_VALIDATE), eq("REJECTED"), anyLong(), eq("表不在白名单"));
    }

    @Test
    void emptyGenerateIsTracedFailed() {
        stubDataQuery();
        Map<String, Object> data = new HashMap<>();
        data.put("sql", null);
        when(toolExecutor.execute(eq(ToolNames.SQL_GENERATE), any(), any()))
                .thenReturn(ToolResult.ok(data));
        runner.run(new AgentRequest("analysis_query", "q", "s1"));
        verify(requestTraceService).stepTool(eq(ToolNames.SQL_GENERATE), eq("FAILED"), anyLong(), any());
    }

    private void stubDataQuery() {
        when(queryRewriteService.rewrite(any(), eq("q"))).thenReturn("q");
        when(intentService.detectWithDetail("q"))
                .thenReturn(new IntentService.IntentDecision(IntentTypeEnum.DATA_QUERY, IntentService.Layer.RULE, 1.0));
        when(routerPlanner.toolsFor(IntentTypeEnum.DATA_QUERY)).thenReturn(List.of(
                ToolNames.SQL_GENERATE, ToolNames.SQL_VALIDATE, ToolNames.SQL_EXECUTE_READONLY, ToolNames.DATA_PROFILE));
    }
}
