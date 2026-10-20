package com.huiyi.assistant.knowledge;

import com.huiyi.assistant.mapper.AsyncTaskMapper;
import com.huiyi.assistant.model.entity.AsyncTask;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AsyncTaskService {

    public static final String TYPE_DOC_PARSE = "DOC_PARSE";
    public static final String RESOURCE_KNOWLEDGE_DOC = "knowledge_doc";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    private final AsyncTaskMapper asyncTaskMapper;

    public AsyncTaskService(AsyncTaskMapper asyncTaskMapper) {
        this.asyncTaskMapper = asyncTaskMapper;
    }

    public AsyncTask enqueueDocParse(long workspaceId, long userId, long docId) {
        AsyncTask task = new AsyncTask();
        task.setTaskType(TYPE_DOC_PARSE);
        task.setWorkspaceId(workspaceId);
        task.setUserId(userId);
        task.setResourceType(RESOURCE_KNOWLEDGE_DOC);
        task.setResourceId(String.valueOf(docId));
        task.setStatus(STATUS_PENDING);
        task.setAttempts(0);
        task.setMaxAttempts(1);
        asyncTaskMapper.insert(task);
        return task;
    }

    public void fail(AsyncTask task, String errorMessage) {
        if (task == null) {
            return;
        }
        task.setStatus(STATUS_FAILED);
        task.setErrorMessage(errorMessage);
        task.setFinishedAt(LocalDateTime.now());
        asyncTaskMapper.updateById(task);
    }
}
