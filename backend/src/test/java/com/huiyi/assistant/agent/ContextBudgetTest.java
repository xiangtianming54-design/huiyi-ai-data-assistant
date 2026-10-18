package com.huiyi.assistant.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBudgetTest {

    @Test
    void dropsKnowledgeBeforeHistoryAndThrowsWhenUntrimmable() {
        ContextBudget budget = new ContextBudget(50);
        String knowledge = "K".repeat(40);
        String history = "H".repeat(40);
        String fittedK = budget.trimKnowledge(knowledge);
        assertTrue(fittedK.length() < knowledge.length());
        budget.assertFits("SYS", fittedK, "", budget.trimHistory(history), "Q");
        ContextBudget tiny = new ContextBudget(3);
        assertThrows(PromptBudgetExceededException.class,
                () -> tiny.assertFits("SYSTEM", "", "", "", "QUESTION"));
    }
}
