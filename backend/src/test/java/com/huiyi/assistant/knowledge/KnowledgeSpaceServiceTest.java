package com.huiyi.assistant.knowledge;

import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.mapper.KnowledgeSpaceMapper;
import com.huiyi.assistant.model.entity.KnowledgeSpace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeSpaceServiceTest {

    @Mock KnowledgeSpaceMapper knowledgeSpaceMapper;
    @Mock AuditService auditService;

    KnowledgeSpaceService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeSpaceService(knowledgeSpaceMapper, auditService);
    }

    @Test
    void ensureDefaultIsIdempotent() {
        when(knowledgeSpaceMapper.selectOne(any())).thenReturn(null).thenAnswer(invocation -> {
            KnowledgeSpace existing = new KnowledgeSpace();
            existing.setId(7L);
            existing.setWorkspaceId(9L);
            existing.setIsDefault(true);
            return existing;
        });
        when(knowledgeSpaceMapper.insert(any(KnowledgeSpace.class))).thenAnswer(invocation -> {
            KnowledgeSpace space = invocation.getArgument(0);
            space.setId(7L);
            return 1;
        });
        KnowledgeSpace first = service.ensureDefault(9L, 1L);
        KnowledgeSpace second = service.ensureDefault(9L, 1L);
        assertEquals(7L, first.getId());
        assertEquals(7L, second.getId());
        verify(knowledgeSpaceMapper, times(1)).insert(any(KnowledgeSpace.class));
    }

    @Test
    void archiveDefaultSpaceRejected() {
        KnowledgeSpace def = new KnowledgeSpace();
        def.setId(1L);
        def.setWorkspaceId(9L);
        def.setIsDefault(true);
        def.setStatus("ACTIVE");
        when(knowledgeSpaceMapper.selectById(1L)).thenReturn(def);
        BizException ex = assertThrows(BizException.class, () -> service.archive(9L, 1L));
        assertEquals(400, ex.getCode());
        assertEquals("默认知识空间不可归档", ex.getMessage());
    }

    @Test
    void listActiveIdsOmitsArchived() {
        KnowledgeSpace active = new KnowledgeSpace();
        active.setId(100L);
        active.setStatus("ACTIVE");
        KnowledgeSpace archived = new KnowledgeSpace();
        archived.setId(102L);
        archived.setStatus("ARCHIVED");
        when(knowledgeSpaceMapper.selectList(any())).thenReturn(List.of(active));
        assertEquals(List.of(100L), service.listActiveIds(9L));
    }
}
