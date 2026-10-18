package com.huiyi.assistant.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSchemaValidatorTest {

    @Test
    void fiveToolSchemasMatchRouterArgs() {
        ToolSchemaValidator v = new ToolSchemaValidator();
        assertTrue(v.isValid(ToolSchemas.SQL_GENERATE, Map.of()));
        assertTrue(v.isValid(ToolSchemas.KNOWLEDGE_SEARCH, Map.of()));
        assertTrue(v.isValid(ToolSchemas.DATA_PROFILE, Map.of()));
        assertTrue(v.isValid(ToolSchemas.SQL_VALIDATE, Map.of("sql", "select 1")));
        assertFalse(v.isValid(ToolSchemas.SQL_VALIDATE, Map.of()));
        assertFalse(v.isValid(ToolSchemas.SQL_VALIDATE, Map.of("sql", "")));
        assertFalse(v.isValid(ToolSchemas.SQL_VALIDATE, Map.of("sql", "select 1", "workspaceId", 1)));
        assertFalse(v.isValid(ToolSchemas.SQL_GENERATE, Map.of("foo", 1)));
    }
}
