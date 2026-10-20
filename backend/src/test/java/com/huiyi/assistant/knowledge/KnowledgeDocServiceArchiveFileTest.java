package com.huiyi.assistant.knowledge;

import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.mapper.AsyncTaskMapper;
import com.huiyi.assistant.mapper.KnowledgeDocMapper;
import com.huiyi.assistant.model.entity.KnowledgeDoc;
import com.huiyi.assistant.service.KnowledgeDocService;
import com.huiyi.assistant.service.PythonServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocServiceArchiveFileTest {

    @Mock PythonServiceClient pythonServiceClient;
    @Mock KnowledgeDocMapper knowledgeDocMapper;
    @Mock AuditService auditService;
    @Mock KnowledgeSpaceService knowledgeSpaceService;
    @Mock LocalFileStorageClient storage;
    @Mock AsyncTaskService asyncTasks;
    @Mock AsyncTaskMapper asyncTaskMapper;
    @Mock TaskExecutor knowledgeParseExecutor;

    KnowledgeDocService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeDocService(pythonServiceClient, knowledgeDocMapper, auditService,
                knowledgeSpaceService, storage, asyncTasks, asyncTaskMapper, knowledgeParseExecutor);
    }

    @Test
    void archiveDeletesLocalFileAndVectors() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(1L);
        doc.setWorkspaceId(9L);
        doc.setStorageKey("9/1/a.md");
        doc.setDocName("a.md");
        when(knowledgeDocMapper.selectById(1L)).thenReturn(doc);

        service.archiveDoc(1L, 9L, 8L);

        verify(pythonServiceClient).deleteVectorsByDoc(9L, 1L);
        verify(storage).delete("9/1/a.md");
    }
}
