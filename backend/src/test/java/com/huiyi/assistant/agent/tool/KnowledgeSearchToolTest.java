package com.huiyi.assistant.agent.tool;

import com.huiyi.assistant.agent.ToolContext;
import com.huiyi.assistant.agent.ToolNames;
import com.huiyi.assistant.agent.ToolResult;
import com.huiyi.assistant.service.RagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeSearchToolTest {

    @Mock RagService ragService;
    @InjectMocks KnowledgeSearchTool tool;

    @Test
    void usesContextWorkspaceNotArgument() {
        when(ragService.prepare(eq("q"), eq(2L)))
                .thenReturn(new RagService.RagContext("fb", null, List.of()));
        ToolResult result = tool.execute(new ToolContext(2L, 9L, "q"),
                Map.of("workspaceId", 99L, "question", "other"));
        verify(ragService).prepare("q", 2L);
        verify(ragService, never()).prepare(eq("q"), eq(99L));
        assertTrue(result.ok());
        assertEquals("fb", result.data().get("fallbackAnswer"));
        assertEquals(ToolNames.KNOWLEDGE_SEARCH, tool.spec().name());
    }
}
