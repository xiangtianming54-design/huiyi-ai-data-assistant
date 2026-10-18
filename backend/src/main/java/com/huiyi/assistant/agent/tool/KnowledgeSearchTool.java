package com.huiyi.assistant.agent.tool;

import com.huiyi.assistant.agent.AgentTool;
import com.huiyi.assistant.agent.ToolContext;
import com.huiyi.assistant.agent.ToolNames;
import com.huiyi.assistant.agent.ToolResult;
import com.huiyi.assistant.agent.ToolSchemas;
import com.huiyi.assistant.agent.ToolSpec;
import com.huiyi.assistant.service.RagService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class KnowledgeSearchTool implements AgentTool {

    private final RagService ragService;

    public KnowledgeSearchTool(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(ToolNames.KNOWLEDGE_SEARCH, "知识检索", ToolSchemas.KNOWLEDGE_SEARCH, 15_000L,
                ToolSpec.DANGER_READ, ToolSpec.PERMISSION_WORKSPACE_MEMBER);
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        RagService.RagContext rag = ragService.prepare(context.question(), context.workspaceId());
        Map<String, Object> data = new HashMap<>();
        data.put("fallbackAnswer", rag.fallbackAnswer());
        data.put("prompt", rag.prompt());
        data.put("references", rag.references());
        return ToolResult.ok(data);
    }
}
