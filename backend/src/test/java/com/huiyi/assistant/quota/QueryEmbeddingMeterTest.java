package com.huiyi.assistant.quota;

import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.context.WorkspaceContextHolder;
import com.huiyi.assistant.observability.LlmCallRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 提问检索的查询向量化：有工作区用户才记账；服务商用量优先，否则本地计数。
 *
 * @author huiyi
 */
@ExtendWith(MockitoExtension.class)
class QueryEmbeddingMeterTest {

    @Mock
    private LlmCallRecorder recorder;

    private QueryEmbeddingMeter meter;

    @BeforeEach
    void setUp() {
        WorkspaceContextHolder.clear();
        meter = new QueryEmbeddingMeter(recorder);
    }

    @AfterEach
    void tearDown() {
        WorkspaceContextHolder.clear();
    }

    @Test
    void nullProviderUsesLocalCountOfTheQuery() {
        WorkspaceContextHolder.set(new WorkspaceContext(9L, 2L, 1L, "MEMBER", "ACTIVE", "ACTIVE"));

        meter.record("订单", null);

        verify(recorder).record("query_embedding", "SUCCESS", LocalTokenCounter.count("订单"), 0, 0, null);
    }

    @Test
    void providerTokensAreRecorded() {
        WorkspaceContextHolder.set(new WorkspaceContext(9L, 2L, 1L, "MEMBER", "ACTIVE", "ACTIVE"));

        meter.record("订单", 12);

        verify(recorder).record("query_embedding", "SUCCESS", 12, 0, 0, null);
    }

    @Test
    void emptyWorkspaceHolderDoesNotCallRecorder() {
        meter.record("订单", 12);

        verify(recorder, never()).record("query_embedding", "SUCCESS", 12, 0, 0, null);
        verifyNoInteractions(recorder);
    }
}
