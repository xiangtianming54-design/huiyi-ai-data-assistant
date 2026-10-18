package com.huiyi.assistant.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentToolConfigurationTest {

    @Test
    void registeredNamesAreExactlyTheFiveReadTools() {
        List<AgentTool> tools = List.of(
                fake(ToolNames.KNOWLEDGE_SEARCH),
                fake(ToolNames.SQL_GENERATE),
                fake(ToolNames.SQL_VALIDATE),
                fake(ToolNames.SQL_EXECUTE_READONLY),
                fake(ToolNames.DATA_PROFILE));
        ToolRegistry registry = new AgentToolConfiguration().toolRegistry(tools);
        assertEquals(Set.of(
                "knowledge.search", "sql.generate", "sql.validate",
                "sql.execute_readonly", "data.profile"), registry.names());
        assertFalse(registry.registered("sql.execute_write"));
        assertFalse(registry.registered("chart.recommend"));
        assertFalse(registry.registered("time.now"));
    }

    private static AgentTool fake(String name) {
        ToolSpec spec = new ToolSpec(name, "t", "{}", 1000L, ToolSpec.DANGER_READ,
                ToolSpec.PERMISSION_WORKSPACE_MEMBER);
        return new AgentTool() {
            @Override
            public ToolSpec spec() {
                return spec;
            }

            @Override
            public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
                return ToolResult.ok(Map.of());
            }
        };
    }
}
