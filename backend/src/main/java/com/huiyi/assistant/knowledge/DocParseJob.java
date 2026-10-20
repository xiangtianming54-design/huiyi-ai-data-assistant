package com.huiyi.assistant.knowledge;

import com.huiyi.assistant.mapper.AsyncTaskMapper;
import com.huiyi.assistant.mapper.KnowledgeDocMapper;
import com.huiyi.assistant.model.dto.ParseResultDTO;
import com.huiyi.assistant.model.entity.AsyncTask;
import com.huiyi.assistant.model.entity.KnowledgeDoc;
import com.huiyi.assistant.model.enums.DocStatusEnum;
import com.huiyi.assistant.service.PythonServiceClient;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
public final class DocParseJob implements Runnable {

    private final AsyncTask task;
    private final KnowledgeDocMapper docs;
    private final AsyncTaskMapper tasks;
    private final LocalFileStorageClient storage;
    private final PythonServiceClient python;

    public DocParseJob(AsyncTask task, KnowledgeDocMapper docs, AsyncTaskMapper tasks,
                       LocalFileStorageClient storage, PythonServiceClient python) {
        this.task = task;
        this.docs = docs;
        this.tasks = tasks;
        this.storage = storage;
        this.python = python;
    }

    @Override
    public void run() {
        KnowledgeDoc doc = null;
        try {
            markRunning();
            long docId = Long.parseLong(task.getResourceId());
            doc = docs.selectById(docId);
            if (doc == null) {
                failTask("文档不存在: " + docId);
                return;
            }
            if (archived(doc)) {
                failTask("文档已归档");
                return;
            }
            doc.setStatus(DocStatusEnum.PARSING.name());
            doc.setErrorMessage(null);
            docs.updateById(doc);
            ParseResultDTO result = python.parseDocument(storage.resolve(doc.getStorageKey()), doc.getDocName());
            if (result == null) {
                throw new IllegalStateException("解析结果为空");
            }
            KnowledgeDoc latest = docs.selectById(docId);
            if (archived(latest)) {
                failTask("文档已归档");
                return;
            }
            python.upsertVectors(result, doc.getWorkspaceId(), doc.getId(), doc.getKnowledgeSpaceId());
            latest = docs.selectById(docId);
            if (archived(latest)) {
                python.deleteVectorsByDoc(doc.getWorkspaceId(), doc.getId());
                failTask("文档已归档");
                return;
            }
            if (result.getFilename() != null) {
                doc.setDocName(result.getFilename());
            }
            doc.setChunkCount(result.getChunkCount());
            doc.setStatus(DocStatusEnum.READY.name());
            doc.setErrorMessage(null);
            docs.updateById(doc);
            task.setStatus(AsyncTaskService.STATUS_SUCCEEDED);
            task.setErrorMessage(null);
            task.setFinishedAt(LocalDateTime.now());
            tasks.updateById(task);
        } catch (Exception e) {
            log.warn("DOC_PARSE failed: taskId={}, err={}", task.getId(), e.getMessage());
            String message = trim(e.getMessage(), 1000);
            KnowledgeDoc latest = reload(doc);
            if (archived(latest)) {
                failTask("文档已归档");
                return;
            }
            if (latest != null) {
                latest.setStatus(DocStatusEnum.FAILED.name());
                latest.setErrorMessage(message);
                docs.updateById(latest);
            }
            failTask(message);
        }
    }

    private KnowledgeDoc reload(KnowledgeDoc snapshot) {
        if (snapshot == null || snapshot.getId() == null) {
            return snapshot;
        }
        KnowledgeDoc latest = docs.selectById(snapshot.getId());
        return latest == null ? snapshot : latest;
    }

    private static boolean archived(KnowledgeDoc doc) {
        return doc != null && DocStatusEnum.ARCHIVED.name().equals(doc.getStatus());
    }

    private void markRunning() {
        task.setStatus(AsyncTaskService.STATUS_RUNNING);
        int attempts = task.getAttempts() == null ? 0 : task.getAttempts();
        task.setAttempts(attempts + 1);
        task.setStartedAt(LocalDateTime.now());
        tasks.updateById(task);
    }

    private void failTask(String message) {
        task.setStatus(AsyncTaskService.STATUS_FAILED);
        task.setErrorMessage(message);
        task.setFinishedAt(LocalDateTime.now());
        tasks.updateById(task);
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "解析失败";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
