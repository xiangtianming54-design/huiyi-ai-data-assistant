package com.huiyi.assistant.agent;

import com.huiyi.assistant.model.enums.IntentTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RouterPlannerTest {

    private final RouterPlanner planner = new RouterPlanner();

    @Test
    void knowledgeQaUsesSearchOnly() {
        assertEquals(List.of("knowledge.search"), planner.toolsFor(IntentTypeEnum.KNOWLEDGE_QA));
    }

    @Test
    void dataQueryUsesSqlThenProfile() {
        assertEquals(List.of("sql.generate", "sql.validate", "sql.execute_readonly", "data.profile"),
                planner.toolsFor(IntentTypeEnum.DATA_QUERY));
        assertFalse(planner.toolsFor(IntentTypeEnum.DATA_QUERY).contains("chart.recommend"));
    }
}
