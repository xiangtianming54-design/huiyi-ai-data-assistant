package com.huiyi.assistant.agent.tool;

import com.huiyi.assistant.agent.ToolContext;
import com.huiyi.assistant.agent.ToolNames;
import com.huiyi.assistant.agent.ToolResult;
import com.huiyi.assistant.knowledge.KnowledgeSpaceResolver;
import com.huiyi.assistant.knowledge.KnowledgeSpaceService;
import com.huiyi.assistant.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @Mock KnowledgeSpaceService knowledgeSpaceService;

    KnowledgeSearchTool tool;

    @BeforeEach
    void setUp() {
        tool = new KnowledgeSearchTool(ragService, knowledgeSpaceService, new KnowledgeSpaceResolver());
    }

    @Test
    void usesContextWorkspaceNotArgument() {
        when(knowledgeSpaceService.listActiveIds(2L)).thenReturn(List.of(100L, 101L));
        when(ragService.prepare(eq("q"), eq(2L), eq(List.of(100L, 101L))))
                .thenReturn(new RagService.RagContext("fb", null, List.of()));
        ToolResult result = tool.execute(new ToolContext(2L, 9L, "q"),
                Map.of("workspaceId", 99L, "question", "other"));
        verify(ragService).prepare("q", 2L, List.of(100L, 101L));
        verify(ragService, never()).prepare(eq("q"), eq(99L), eq(List.of(100L, 101L)));
        assertTrue(result.ok());
        assertEquals("fb", result.data().get("fallbackAnswer"));
        assertEquals(ToolNames.KNOWLEDGE_SEARCH, tool.spec().name());
    }

    @Test
    void intersectsRequestedIds() {
        when(knowledgeSpaceService.listActiveIds(2L)).thenReturn(List.of(100L, 101L));
        when(ragService.prepare(eq("q"), eq(2L), eq(List.of(101L))))
                .thenReturn(new RagService.RagContext("fb", null, List.of()));
        tool.execute(new ToolContext(2L, 9L, "q"), Map.of("spaceIds", List.of(101, 999)));
        verify(ragService).prepare("q", 2L, List.of(101L));
    }
}
