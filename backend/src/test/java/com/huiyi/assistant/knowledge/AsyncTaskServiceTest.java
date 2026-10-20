package com.huiyi.assistant.knowledge;

import com.huiyi.assistant.mapper.AsyncTaskMapper;
import com.huiyi.assistant.model.entity.AsyncTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncTaskServiceTest {

    @Mock AsyncTaskMapper asyncTaskMapper;

    AsyncTaskService service;

    @BeforeEach
    void setUp() {
        service = new AsyncTaskService(asyncTaskMapper);
    }

    @Test
    void enqueueDocParseInsertsPendingRow() {
        when(asyncTaskMapper.insert(any(AsyncTask.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AsyncTask.class).setId(3L);
            return 1;
        });
        AsyncTask task = service.enqueueDocParse(9L, 1L, 55L);
        ArgumentCaptor<AsyncTask> captor = ArgumentCaptor.forClass(AsyncTask.class);
        verify(asyncTaskMapper).insert(captor.capture());
        AsyncTask inserted = captor.getValue();
        assertEquals("DOC_PARSE", inserted.getTaskType());
        assertEquals("knowledge_doc", inserted.getResourceType());
        assertEquals("55", inserted.getResourceId());
        assertEquals("PENDING", inserted.getStatus());
        assertEquals(1, inserted.getMaxAttempts());
        assertEquals(0, inserted.getAttempts());
        assertEquals(9L, inserted.getWorkspaceId());
        assertEquals(1L, inserted.getUserId());
        assertEquals(3L, task.getId());
    }
}
