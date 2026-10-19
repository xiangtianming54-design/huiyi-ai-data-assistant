package com.huiyi.assistant.knowledge;

import com.huiyi.assistant.mapper.KnowledgeDocMapper;
import com.huiyi.assistant.model.dto.VectorBackfillRequestDTO;
import com.huiyi.assistant.model.entity.KnowledgeDoc;
import com.huiyi.assistant.service.PythonServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeSpaceVectorBackfillTest {

    @Mock KnowledgeDocMapper knowledgeDocMapper;
    @Mock PythonServiceClient pythonServiceClient;

    @Test
    void enabledPostsAssignments() throws Exception {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(55L);
        doc.setWorkspaceId(9L);
        doc.setKnowledgeSpaceId(100L);
        when(knowledgeDocMapper.selectList(any())).thenReturn(List.of(doc));

        new KnowledgeSpaceVectorBackfill(knowledgeDocMapper, pythonServiceClient, true)
                .run(new DefaultApplicationArguments());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VectorBackfillRequestDTO.Assignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(pythonServiceClient).backfillSpaceIds(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(9L, captor.getValue().get(0).getWorkspaceId());
        assertEquals(55L, captor.getValue().get(0).getDocId());
        assertEquals(100L, captor.getValue().get(0).getKnowledgeSpaceId());
    }

    @Test
    void disabledSkipsPython() throws Exception {
        new KnowledgeSpaceVectorBackfill(knowledgeDocMapper, pythonServiceClient, false)
                .run(new DefaultApplicationArguments());
        verify(pythonServiceClient, never()).backfillSpaceIds(any());
        verify(knowledgeDocMapper, never()).selectList(any());
    }
}
