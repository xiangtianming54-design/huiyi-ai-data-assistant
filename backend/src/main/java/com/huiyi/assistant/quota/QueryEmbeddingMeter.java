package com.huiyi.assistant.quota;

import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.context.WorkspaceContextHolder;
import com.huiyi.assistant.observability.LlmCallRecorder;
import org.springframework.stereotype.Component;

/**
 * 把一次提问检索的查询向量化记到当前用户。没有工作区用户时不调用 recorder。不计耗时。
 *
 * @author huiyi
 */
@Component
public class QueryEmbeddingMeter {

    private final LlmCallRecorder llmCallRecorder;

    public QueryEmbeddingMeter(LlmCallRecorder llmCallRecorder) {
        this.llmCallRecorder = llmCallRecorder;
    }

    /**
     * 记一条 purpose 为 query_embedding、状态 SUCCESS、completion 为 0 的调用。
     *
     * @param query 送去向量化的查询文本
     * @param providerTokens 服务商 usage.total_tokens；没有时用本地计数
     */
    public void record(String query, Integer providerTokens) {
        WorkspaceContext workspace = WorkspaceContextHolder.get();
        if (workspace == null || workspace.userId() == null) {
            return;
        }
        int promptTokens = providerTokens != null ? providerTokens : LocalTokenCounter.count(query);
        llmCallRecorder.record("query_embedding", "SUCCESS", promptTokens, 0, 0, null);
    }
}
