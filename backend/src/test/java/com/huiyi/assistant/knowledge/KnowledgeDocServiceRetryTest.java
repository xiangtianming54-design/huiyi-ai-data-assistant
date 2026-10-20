package com.huiyi.assistant.knowledge;

import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.mapper.AsyncTaskMapper;
import com.huiyi.assistant.mapper.KnowledgeDocMapper;
import com.huiyi.assistant.model.entity.KnowledgeDoc;
import com.huiyi.assistant.model.vo.KnowledgeDocVO;
import com.huiyi.assistant.service.KnowledgeDocService;
import com.huiyi.assistant.service.PythonServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocServiceRetryTest {

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

    private KnowledgeDoc doc(String status) {
        KnowledgeDoc d = new KnowledgeDoc();
        d.setId(55L);
        d.setWorkspaceId(9L);
        d.setStatus(status);
        d.setStorageKey("9/55/a.md");
        d.setDocName("a.md");
        return d;
    }

    @Test
    void retryFailedEnqueuesNewParse() {
        when(knowledgeDocMapper.selectById(55L)).thenReturn(doc("FAILED"));
        when(storage.exists("9/55/a.md")).thenReturn(true);
        KnowledgeDocVO vo = service.retry(55L, 9L, 1L);
        assertEquals("UPLOADED", vo.getStatus());
        verify(asyncTasks).enqueueDocParse(9L, 1L, 55L);
        verify(knowledgeParseExecutor).execute(any(Runnable.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY", "PARSING", "UPLOADED", "ARCHIVED"})
    void retryNonFailedRejected(String status) {
        when(knowledgeDocMapper.selectById(55L)).thenReturn(doc(status));
        BizException ex = assertThrows(BizException.class, () -> service.retry(55L, 9L, 1L));
        assertEquals(400, ex.getCode());
        assertEquals("仅失败的文档可重试", ex.getMessage());
    }

    @Test
    void retryFailedWithoutFileRejected() {
        when(knowledgeDocMapper.selectById(55L)).thenReturn(doc("FAILED"));
        when(storage.exists("9/55/a.md")).thenReturn(false);
        BizException ex = assertThrows(BizException.class, () -> service.retry(55L, 9L, 1L));
        assertEquals("原文件不存在，无法重试", ex.getMessage());
    }
}
