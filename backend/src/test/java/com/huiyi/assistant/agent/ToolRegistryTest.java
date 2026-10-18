package com.huiyi.assistant.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    @Test
    void requireUnknownThrows() {
        ToolRegistry registry = new ToolRegistry();
        UnknownToolException ex = assertThrows(UnknownToolException.class,
                () -> registry.require("sql.execute_write"));
        assertEquals("sql.execute_write", ex.toolName());
    }

    @Test
    void registerThenRequireReturnsSameTool() {
        ToolRegistry registry = new ToolRegistry();
        AgentTool tool = fakeTool(ToolNames.KNOWLEDGE_SEARCH);
        registry.register(tool);
        assertSame(tool, registry.require(ToolNames.KNOWLEDGE_SEARCH));
        assertTrue(registry.names().contains(ToolNames.KNOWLEDGE_SEARCH));
    }

    @Test
    void writeToolsAreNotRegisteredByDefault() {
        ToolRegistry registry = new ToolRegistry();
        assertFalse(registry.registered("sql.execute_write"));
        assertFalse(registry.registered("time.now"));
        assertFalse(registry.registered("chart.recommend"));
    }

    @Test
    void duplicateRegisterThrows() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(fakeTool(ToolNames.KNOWLEDGE_SEARCH));
        assertThrows(IllegalStateException.class,
                () -> registry.register(fakeTool(ToolNames.KNOWLEDGE_SEARCH)));
    }

    private static AgentTool fakeTool(String name) {
        ToolSpec spec = new ToolSpec(name, "fake", "{}", 1000L, ToolSpec.DANGER_READ,
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
