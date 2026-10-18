package com.huiyi.assistant.agent.tool;

import com.huiyi.assistant.agent.AgentTool;
import com.huiyi.assistant.agent.ToolContext;
import com.huiyi.assistant.agent.ToolNames;
import com.huiyi.assistant.agent.ToolResult;
import com.huiyi.assistant.agent.ToolSchemas;
import com.huiyi.assistant.agent.ToolSpec;
import com.huiyi.assistant.model.dto.SqlValidateResponseDTO;
import com.huiyi.assistant.service.PythonServiceClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SqlValidateTool implements AgentTool {

    private final PythonServiceClient pythonServiceClient;

    public SqlValidateTool(PythonServiceClient pythonServiceClient) {
        this.pythonServiceClient = pythonServiceClient;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(ToolNames.SQL_VALIDATE, "校验 SQL", ToolSchemas.SQL_VALIDATE, 10_000L,
                ToolSpec.DANGER_READ, ToolSpec.PERMISSION_WORKSPACE_MEMBER);
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        Object raw = arguments == null ? null : arguments.get("sql");
        if (!(raw instanceof String sql) || sql.isBlank()) {
            return ToolResult.fail("missing sql");
        }
        SqlValidateResponseDTO validation = pythonServiceClient.validateSql(sql);
        Map<String, Object> data = new HashMap<>();
        if (validation == null) {
            data.put("valid", false);
            data.put("sql", sql);
            data.put("reason", "校验服务异常");
            return ToolResult.ok(data);
        }
        data.put("valid", validation.getValid());
        data.put("sql", validation.getSql());
        data.put("reason", validation.getReason());
        return ToolResult.ok(data);
    }
}
