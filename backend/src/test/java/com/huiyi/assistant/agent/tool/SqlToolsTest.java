package com.huiyi.assistant.agent.tool;

import com.huiyi.assistant.agent.ToolContext;
import com.huiyi.assistant.agent.ToolNames;
import com.huiyi.assistant.agent.ToolResult;
import com.huiyi.assistant.agent.ToolSchemas;
import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.model.dto.SqlValidateResponseDTO;
import com.huiyi.assistant.service.PythonServiceClient;
import com.huiyi.assistant.service.SqlExecuteService;
import com.huiyi.assistant.service.TextToSqlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlToolsTest {

    @Mock TextToSqlService textToSqlService;
    @Mock PythonServiceClient pythonServiceClient;
    @Mock SqlExecuteService sqlExecuteService;

    @Test
    void generateSpecIsNotEmptyObjectSchema() {
        assertNotEquals("{}", new SqlGenerateTool(textToSqlService).spec().inputSchema());
    }

    @Test
    void viewerContextCanExecuteReadonly() {
        WorkspaceContext viewer = new WorkspaceContext(9L, 2L, 1L, "VIEWER", "ACTIVE", "ACTIVE");
        when(sqlExecuteService.execute("select 1", "q")).thenReturn(List.of(Map.of("n", 1)));
        SqlExecuteReadonlyTool tool = new SqlExecuteReadonlyTool(sqlExecuteService);
        ToolResult result = tool.execute(
                new ToolContext(viewer.workspaceId(), viewer.userId(), "q"),
                Map.of("sql", "select 1"));
        assertTrue(result.ok());
        verify(sqlExecuteService).execute("select 1", "q");
        assertEquals(ToolNames.SQL_EXECUTE_READONLY, tool.spec().name());
        assertEquals(ToolSpecRead.permissions(), tool.spec().permissions());
        assertTrue(tool.spec().timeoutMs() > 5_000L);
    }

    @Test
    void generateAndValidateWrapExistingServices() {
        when(textToSqlService.generateSql("q")).thenReturn("select 1");
        SqlValidateResponseDTO validation = new SqlValidateResponseDTO();
        validation.setValid(true);
        validation.setSql("select 1");
        when(pythonServiceClient.validateSql("select 1")).thenReturn(validation);

        ToolResult generated = new SqlGenerateTool(textToSqlService)
                .execute(new ToolContext(2L, 9L, "q"), Map.of());
        assertEquals("select 1", generated.data().get("sql"));

        ToolResult validated = new SqlValidateTool(pythonServiceClient)
                .execute(new ToolContext(2L, 9L, "q"), Map.of("sql", "select 1"));
        assertEquals(Boolean.TRUE, validated.data().get("valid"));
    }

    private static final class ToolSpecRead {
        static String permissions() {
            return com.huiyi.assistant.agent.ToolSpec.PERMISSION_WORKSPACE_MEMBER;
        }
    }
}
