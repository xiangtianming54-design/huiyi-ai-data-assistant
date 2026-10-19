package com.huiyi.assistant.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeSpaceResolverTest {

    @Test
    void resolveIntersectsAndKeepsActiveOrder() {
        KnowledgeSpaceResolver r = new KnowledgeSpaceResolver();
        List<Long> active = List.of(100L, 101L);
        assertEquals(List.of(100L, 101L), r.resolve(null, null, active));
        assertEquals(List.of(), r.resolve(List.of(), null, active));
        assertEquals(List.of(101L), r.resolve(List.of(101L, 999L), null, active));
        assertEquals(List.of(), r.resolve(List.of(101L), List.of(100L), active));
        assertEquals(List.of(101L), r.resolve(List.of(100L, 101L), List.of(101L), active));
    }
}
