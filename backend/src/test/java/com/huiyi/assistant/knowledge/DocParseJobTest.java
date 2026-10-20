package com.huiyi.assistant.knowledge;

import com.huiyi.assistant.mapper.AsyncTaskMapper;
import com.huiyi.assistant.mapper.KnowledgeDocMapper;
import com.huiyi.assistant.model.dto.ParseResultDTO;
import com.huiyi.assistant.model.entity.AsyncTask;
import com.huiyi.assistant.model.entity.KnowledgeDoc;
import com.huiyi.assistant.service.PythonServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocParseJobTest {

    @Mock KnowledgeDocMapper docs;
    @Mock AsyncTaskMapper tasks;
    @Mock LocalFileStorageClient storage;
    @Mock PythonServiceClient python;

    AsyncTask task;
    KnowledgeDoc doc;

    @BeforeEach
    void setUp() {
        task = new AsyncTask();
        task.setId(3L);
        task.setResourceId("55");
        task.setAttempts(0);
        task.setStatus("PENDING");
        doc = new KnowledgeDoc();
        doc.setId(55L);
        doc.setWorkspaceId(9L);
        doc.setKnowledgeSpaceId(100L);
        doc.setDocName("a.md");
        doc.setStorageKey("9/55/a.md");
        doc.setStatus("UPLOADED");
        when(docs.selectById(55L)).thenReturn(doc);
    }

    @Test
    void runSuccessMarksReadyAndUpserts() {
        ParseResultDTO parsed = new ParseResultDTO();
        parsed.setFilename("a.md");
        parsed.setChunkCount(2);
        Path path = Path.of("9", "55", "a.md");
        when(storage.resolve("9/55/a.md")).thenReturn(path);
        when(python.parseDocument(path, "a.md")).thenReturn(parsed);

        new DocParseJob(task, docs, tasks, storage, python).run();

        ArgumentCaptor<KnowledgeDoc> docCaptor = ArgumentCaptor.forClass(KnowledgeDoc.class);
        verify(docs, atLeastOnce()).updateById(docCaptor.capture());
        KnowledgeDoc ready = docCaptor.getAllValues().get(docCaptor.getAllValues().size() - 1);
        assertEquals("READY", ready.getStatus());
        assertEquals(2, ready.getChunkCount());
        assertNull(ready.getErrorMessage());
        verify(python).upsertVectors(parsed, 9L, 55L, 100L);
        ArgumentCaptor<AsyncTask> taskCaptor = ArgumentCaptor.forClass(AsyncTask.class);
        verify(tasks, atLeastOnce()).updateById(taskCaptor.capture());
        assertEquals("SUCCEEDED", taskCaptor.getAllValues().get(taskCaptor.getAllValues().size() - 1).getStatus());
    }

    @Test
    void runParseFailureMarksFailed() {
        Path path = Path.of("9", "55", "a.md");
        when(storage.resolve("9/55/a.md")).thenReturn(path);
        when(python.parseDocument(path, "a.md")).thenThrow(new RuntimeException("boom"));

        new DocParseJob(task, docs, tasks, storage, python).run();

        ArgumentCaptor<KnowledgeDoc> docCaptor = ArgumentCaptor.forClass(KnowledgeDoc.class);
        verify(docs, atLeastOnce()).updateById(docCaptor.capture());
        KnowledgeDoc failed = docCaptor.getAllValues().get(docCaptor.getAllValues().size() - 1);
        assertEquals("FAILED", failed.getStatus());
        assertTrue(failed.getErrorMessage().contains("boom"));
        verify(python, never()).upsertVectors(any(), any(), any(), any());
        ArgumentCaptor<AsyncTask> taskCaptor = ArgumentCaptor.forClass(AsyncTask.class);
        verify(tasks, atLeastOnce()).updateById(taskCaptor.capture());
        assertEquals("FAILED", taskCaptor.getAllValues().get(taskCaptor.getAllValues().size() - 1).getStatus());
    }

    @Test
    void skipWhenDocAlreadyArchived() {
        doc.setStatus("ARCHIVED");
        new DocParseJob(task, docs, tasks, storage, python).run();
        verify(python, never()).parseDocument(any(), any());
        verify(python, never()).upsertVectors(any(), any(), any(), any());
        verify(docs, never()).updateById(any(KnowledgeDoc.class));
        ArgumentCaptor<AsyncTask> taskCaptor = ArgumentCaptor.forClass(AsyncTask.class);
        verify(tasks, atLeastOnce()).updateById(taskCaptor.capture());
        assertEquals("FAILED", taskCaptor.getAllValues().get(taskCaptor.getAllValues().size() - 1).getStatus());
    }

    @Test
    void skipUpsertIfArchivedDuringParse() {
        KnowledgeDoc archived = new KnowledgeDoc();
        archived.setId(55L);
        archived.setWorkspaceId(9L);
        archived.setStatus("ARCHIVED");
        archived.setStorageKey("9/55/a.md");
        when(docs.selectById(55L)).thenReturn(doc).thenReturn(archived);
        ParseResultDTO parsed = new ParseResultDTO();
        parsed.setChunkCount(2);
        Path path = Path.of("9", "55", "a.md");
        when(storage.resolve("9/55/a.md")).thenReturn(path);
        when(python.parseDocument(path, "a.md")).thenReturn(parsed);

        new DocParseJob(task, docs, tasks, storage, python).run();

        verify(python, never()).upsertVectors(any(), any(), any(), any());
        ArgumentCaptor<KnowledgeDoc> docCaptor = ArgumentCaptor.forClass(KnowledgeDoc.class);
        verify(docs, atLeastOnce()).updateById(docCaptor.capture());
        assertTrue(docCaptor.getAllValues().stream().noneMatch(d -> "READY".equals(d.getStatus())));
        assertTrue(docCaptor.getAllValues().stream().noneMatch(d -> "FAILED".equals(d.getStatus())));
    }

    @Test
    void parseFailureDoesNotOverwriteArchived() {
        KnowledgeDoc archived = new KnowledgeDoc();
        archived.setId(55L);
        archived.setStatus("ARCHIVED");
        when(docs.selectById(55L)).thenReturn(doc).thenReturn(archived);
        Path path = Path.of("9", "55", "a.md");
        when(storage.resolve("9/55/a.md")).thenReturn(path);
        when(python.parseDocument(path, "a.md")).thenThrow(new RuntimeException("no file"));

        new DocParseJob(task, docs, tasks, storage, python).run();

        ArgumentCaptor<KnowledgeDoc> docCaptor = ArgumentCaptor.forClass(KnowledgeDoc.class);
        verify(docs, atLeastOnce()).updateById(docCaptor.capture());
        assertTrue(docCaptor.getAllValues().stream().noneMatch(d -> "FAILED".equals(d.getStatus())));
        assertTrue(docCaptor.getAllValues().stream().noneMatch(d -> "READY".equals(d.getStatus())));
    }
}
