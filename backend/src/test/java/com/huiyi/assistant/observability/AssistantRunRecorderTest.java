package com.huiyi.assistant.observability;

import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.context.WorkspaceContextHolder;
import com.huiyi.assistant.mapper.AssistantRunMapper;
import com.huiyi.assistant.mapper.AssistantRunStepMapper;
import com.huiyi.assistant.model.entity.AssistantRun;
import com.huiyi.assistant.model.entity.AssistantRunStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssistantRunRecorderTest {

    @Mock AssistantRunMapper runMapper;
    @Mock AssistantRunStepMapper stepMapper;

    AssistantRunRecorder recorder;

    @BeforeEach
    void setUp() {
        ObservabilityProperties properties = new ObservabilityProperties();
        recorder = new AssistantRunRecorder(runMapper, stepMapper, properties);
        ObservabilityContextHolder.setRequestId("req_abc123abc123abc123abc123abc123ab");
        WorkspaceContextHolder.set(new WorkspaceContext(9L, 2L, 1L, "OWNER", "ACTIVE", "ACTIVE"));
    }

    @AfterEach
    void tearDown() {
        ObservabilityContextHolder.clear();
        WorkspaceContextHolder.clear();
    }

    @Test
    void startWritesRunWithRequestId() {
        String runId = recorder.start("chat_stream", "什么是有效订单？", "s1");
        assertTrue(runId.startsWith("run_"));
        ArgumentCaptor<AssistantRun> captor = ArgumentCaptor.forClass(AssistantRun.class);
        verify(runMapper).insert(captor.capture());
        AssistantRun run = captor.getValue();
        assertEquals(runId, run.getRunId());
        assertEquals("req_abc123abc123abc123abc123abc123ab", run.getRequestId());
        assertEquals(2L, run.getWorkspaceId());
        assertEquals("RUNNING", run.getStatus());
    }

    @Test
    void stepInsertDoesNotPropagateDbFailure() {
        recorder.start("chat_ask", "q", null);
        doThrow(new RuntimeException("db")).when(stepMapper).insert(any(AssistantRunStep.class));
        assertDoesNotThrow(() -> recorder.step("intent", "SUCCESS", 12, null));
    }
}
