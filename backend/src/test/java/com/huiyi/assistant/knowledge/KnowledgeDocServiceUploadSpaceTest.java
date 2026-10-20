package com.huiyi.assistant.knowledge;

import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.mapper.KnowledgeDocMapper;
import com.huiyi.assistant.model.entity.KnowledgeDoc;
import com.huiyi.assistant.model.entity.KnowledgeSpace;
import com.huiyi.assistant.model.vo.KnowledgeDocVO;
import com.huiyi.assistant.service.KnowledgeDocService;
import com.huiyi.assistant.service.PythonServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocServiceUploadSpaceTest {

    @Mock PythonServiceClient pythonServiceClient;
    @Mock KnowledgeDocMapper knowledgeDocMapper;
    @Mock AuditService auditService;
    @Mock KnowledgeSpaceService knowledgeSpaceService;
    @Mock LocalFileStorageClient storage;
    @Mock AsyncTaskService asyncTasks;
    @Mock TaskExecutor knowledgeParseExecutor;

    KnowledgeDocService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeDocService(pythonServiceClient, knowledgeDocMapper, auditService,
                knowledgeSpaceService, storage, asyncTasks, knowledgeParseExecutor);
    }

    @Test
    void uploadWithoutSpaceIdUsesDefaultSpace() {
        KnowledgeSpace def = new KnowledgeSpace();
        def.setId(100L);
        def.setWorkspaceId(9L);
        def.setStatus("ACTIVE");
        when(knowledgeSpaceService.requireActiveSpace(9L, null)).thenReturn(def);
        when(knowledgeDocMapper.insert(any(KnowledgeDoc.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, KnowledgeDoc.class).setId(55L);
            return 1;
        });
        when(storage.save(eq(9L), eq(55L), any(), eq("a.md"))).thenReturn("9/55/a.md");
        MockMultipartFile file = new MockMultipartFile("file", "a.md", "text/markdown", "hi".getBytes());

        KnowledgeDocVO vo = service.upload(file, 9L, 1L, null);

        ArgumentCaptor<KnowledgeDoc> captor = ArgumentCaptor.forClass(KnowledgeDoc.class);
        verify(knowledgeDocMapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getKnowledgeSpaceId());
        assertEquals("UPLOADED", captor.getValue().getStatus());
        verify(asyncTasks).enqueueDocParse(9L, 1L, 55L);
        verify(knowledgeParseExecutor).execute(any(Runnable.class));
        verify(pythonServiceClient, never()).parseDocument(any(MultipartFile.class));
        verify(pythonServiceClient, never()).upsertVectors(any(), any(), any(), any());
        assertEquals("UPLOADED", vo.getStatus());
        assertEquals(55L, vo.getDocId());
    }

    @Test
    void uploadToArchivedSpaceRejected() {
        when(knowledgeSpaceService.requireActiveSpace(9L, 8L))
                .thenThrow(new BizException(400, "不能向已归档空间上传"));
        MockMultipartFile file = new MockMultipartFile("file", "a.md", "text/markdown", "hi".getBytes());
        BizException ex = assertThrows(BizException.class, () -> service.upload(file, 9L, 1L, 8L));
        assertEquals("不能向已归档空间上传", ex.getMessage());
        verify(pythonServiceClient, never()).parseDocument(any(MultipartFile.class));
        verify(storage, never()).save(anyLong(), anyLong(), any(), any());
    }

    @Test
    void uploadExecutorRejectedMarksFailed() {
        KnowledgeSpace def = new KnowledgeSpace();
        def.setId(100L);
        when(knowledgeSpaceService.requireActiveSpace(9L, null)).thenReturn(def);
        when(knowledgeDocMapper.insert(any(KnowledgeDoc.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, KnowledgeDoc.class).setId(55L);
            return 1;
        });
        when(storage.save(eq(9L), eq(55L), any(), eq("a.md"))).thenReturn("9/55/a.md");
        doThrow(new RejectedExecutionException("full")).when(knowledgeParseExecutor).execute(any(Runnable.class));
        MockMultipartFile file = new MockMultipartFile("file", "a.md", "text/markdown", "hi".getBytes());

        KnowledgeDocVO vo = service.upload(file, 9L, 1L, null);

        assertEquals("FAILED", vo.getStatus());
        verify(pythonServiceClient, never()).parseDocument(any(MultipartFile.class));
    }

    @Test
    void listDocsWithoutSpaceIdUsesActiveSpacesOnly() {
        when(knowledgeSpaceService.listActiveIds(9L)).thenReturn(List.of(100L));
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(1L);
        doc.setKnowledgeSpaceId(100L);
        doc.setStatus("READY");
        when(knowledgeDocMapper.selectList(any())).thenReturn(List.of(doc));
        var page = service.listDocs(9L, 1, 20, null);
        assertEquals(1, page.getItems().size());
        assertEquals(100L, page.getItems().get(0).getKnowledgeSpaceId());
        verify(knowledgeSpaceService).listActiveIds(9L);
    }
}
