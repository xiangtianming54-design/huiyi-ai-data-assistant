package com.huiyi.assistant.agent.tool;

import com.huiyi.assistant.agent.AgentTool;
import com.huiyi.assistant.agent.ToolContext;
import com.huiyi.assistant.agent.ToolNames;
import com.huiyi.assistant.agent.ToolResult;
import com.huiyi.assistant.agent.ToolSchemas;
import com.huiyi.assistant.agent.ToolSpec;
import com.huiyi.assistant.service.TextToSqlService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SqlGenerateTool implements AgentTool {

    private final TextToSqlService textToSqlService;

    public SqlGenerateTool(TextToSqlService textToSqlService) {
        this.textToSqlService = textToSqlService;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(ToolNames.SQL_GENERATE, "生成只读 SQL", ToolSchemas.SQL_GENERATE, 30_000L,
                ToolSpec.DANGER_READ, ToolSpec.PERMISSION_WORKSPACE_MEMBER);
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        String sql = textToSqlService.generateSql(context.question());
        Map<String, Object> data = new HashMap<>();
        data.put("sql", sql);
        return ToolResult.ok(data);
    }
}
