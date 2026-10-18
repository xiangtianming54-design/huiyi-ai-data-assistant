package com.huiyi.assistant.agent.tool;

import com.huiyi.assistant.agent.AgentTool;
import com.huiyi.assistant.agent.ToolContext;
import com.huiyi.assistant.agent.ToolNames;
import com.huiyi.assistant.agent.ToolResult;
import com.huiyi.assistant.agent.ToolSpec;
import com.huiyi.assistant.model.dto.DataProfileResponseDTO;
import com.huiyi.assistant.service.PythonServiceClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataProfileTool implements AgentTool {

    private final PythonServiceClient pythonServiceClient;

    public DataProfileTool(PythonServiceClient pythonServiceClient) {
        this.pythonServiceClient = pythonServiceClient;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(ToolNames.DATA_PROFILE, "数据画像与图表类型", "{}", 10_000L,
                ToolSpec.DANGER_READ, ToolSpec.PERMISSION_WORKSPACE_MEMBER);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        List<Map<String, Object>> rows = List.of();
        Object raw = arguments == null ? null : arguments.get("rows");
        if (raw instanceof List<?> list) {
            rows = (List<Map<String, Object>>) list;
        }
        DataProfileResponseDTO profile = pythonServiceClient.profileData(rows);
        Map<String, Object> data = new HashMap<>();
        if (profile != null) {
            data.put("chartType", profile.getChartType());
            data.put("rowCount", profile.getRowCount());
            data.put("columns", profile.getColumns());
            data.put("summary", profile.getSummary());
            data.put("empty", profile.getEmpty());
        }
        return ToolResult.ok(data);
    }
}
