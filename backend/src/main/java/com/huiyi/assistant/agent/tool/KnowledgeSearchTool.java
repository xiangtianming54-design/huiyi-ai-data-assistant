package com.huiyi.assistant.agent.tool;

import com.huiyi.assistant.agent.AgentTool;
import com.huiyi.assistant.agent.ToolContext;
import com.huiyi.assistant.agent.ToolNames;
import com.huiyi.assistant.agent.ToolResult;
import com.huiyi.assistant.agent.ToolSchemas;
import com.huiyi.assistant.agent.ToolSpec;
import com.huiyi.assistant.knowledge.KnowledgeSpaceResolver;
import com.huiyi.assistant.knowledge.KnowledgeSpaceService;
import com.huiyi.assistant.service.RagService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class KnowledgeSearchTool implements AgentTool {

    private final RagService ragService;
    private final KnowledgeSpaceService knowledgeSpaceService;
    private final KnowledgeSpaceResolver knowledgeSpaceResolver;

    public KnowledgeSearchTool(RagService ragService, KnowledgeSpaceService knowledgeSpaceService,
                               KnowledgeSpaceResolver knowledgeSpaceResolver) {
        this.ragService = ragService;
        this.knowledgeSpaceService = knowledgeSpaceService;
        this.knowledgeSpaceResolver = knowledgeSpaceResolver;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(ToolNames.KNOWLEDGE_SEARCH, "知识检索", ToolSchemas.KNOWLEDGE_SEARCH, 15_000L,
                ToolSpec.DANGER_READ, ToolSpec.PERMISSION_WORKSPACE_MEMBER);
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        List<Long> requested = arguments != null && arguments.containsKey("spaceIds")
                ? parseIds(arguments.get("spaceIds"))
                : null;
        List<Long> effective = knowledgeSpaceResolver.resolve(
                requested, null, knowledgeSpaceService.listActiveIds(context.workspaceId()));
        RagService.RagContext rag = ragService.prepare(context.question(), context.workspaceId(), effective);
        Map<String, Object> data = new HashMap<>();
        data.put("fallbackAnswer", rag.fallbackAnswer());
        data.put("prompt", rag.prompt());
        data.put("references", rag.references());
        return ToolResult.ok(data);
    }

    private static List<Long> parseIds(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<Long> ids = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number number) {
                    ids.add(number.longValue());
                }
            }
            return ids;
        }
        if (raw instanceof Number number) {
            return List.of(number.longValue());
        }
        return List.of();
    }
}
