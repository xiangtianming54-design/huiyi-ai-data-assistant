package com.huiyi.assistant.agent.tool;

import com.huiyi.assistant.agent.AgentTool;
import com.huiyi.assistant.agent.ToolContext;
import com.huiyi.assistant.agent.ToolNames;
import com.huiyi.assistant.agent.ToolResult;
import com.huiyi.assistant.agent.ToolSchemas;
import com.huiyi.assistant.agent.ToolSpec;
import com.huiyi.assistant.service.SqlExecuteService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SqlExecuteReadonlyTool implements AgentTool {

    private final SqlExecuteService sqlExecuteService;

    public SqlExecuteReadonlyTool(SqlExecuteService sqlExecuteService) {
        this.sqlExecuteService = sqlExecuteService;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(ToolNames.SQL_EXECUTE_READONLY, "执行只读 SQL", ToolSchemas.SQL_EXECUTE_READONLY, 15_000L,
                ToolSpec.DANGER_READ, ToolSpec.PERMISSION_WORKSPACE_MEMBER);
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        Object raw = arguments == null ? null : arguments.get("sql");
        if (!(raw instanceof String sql) || sql.isBlank()) {
            return ToolResult.fail("missing sql");
        }
        List<Map<String, Object>> rows = sqlExecuteService.execute(sql, context.question());
        return ToolResult.ok(Map.of("rows", rows));
    }
}
