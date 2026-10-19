package com.huiyi.assistant.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huiyi.assistant.mapper.KnowledgeDocMapper;
import com.huiyi.assistant.model.dto.VectorBackfillRequestDTO;
import com.huiyi.assistant.model.entity.KnowledgeDoc;
import com.huiyi.assistant.service.PythonServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class KnowledgeSpaceVectorBackfill implements ApplicationRunner {

    static final int BATCH_SIZE = 100;

    private final KnowledgeDocMapper knowledgeDocMapper;
    private final PythonServiceClient pythonServiceClient;
    private final boolean enabled;

    public KnowledgeSpaceVectorBackfill(KnowledgeDocMapper knowledgeDocMapper,
                                        PythonServiceClient pythonServiceClient,
                                        @Value("${huiyi.knowledge.backfill-vector-space-ids:true}") boolean enabled) {
        this.knowledgeDocMapper = knowledgeDocMapper;
        this.pythonServiceClient = pythonServiceClient;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("知识空间向量回填已关闭");
            return;
        }
        QueryWrapper<KnowledgeDoc> wrapper = new QueryWrapper<>();
        wrapper.select("id", "workspace_id", "knowledge_space_id")
                .isNotNull("id")
                .isNotNull("workspace_id")
                .isNotNull("knowledge_space_id");
        List<KnowledgeDoc> docs = knowledgeDocMapper.selectList(wrapper);
        if (docs == null || docs.isEmpty()) {
            return;
        }
        List<VectorBackfillRequestDTO.Assignment> batch = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            if (doc == null || doc.getId() == null || doc.getWorkspaceId() == null || doc.getKnowledgeSpaceId() == null) {
                continue;
            }
            batch.add(new VectorBackfillRequestDTO.Assignment(doc.getWorkspaceId(), doc.getId(), doc.getKnowledgeSpaceId()));
            if (batch.size() >= BATCH_SIZE) {
                postBatch(batch);
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) {
            postBatch(batch);
        }
    }

    private void postBatch(List<VectorBackfillRequestDTO.Assignment> batch) {
        try {
            pythonServiceClient.backfillSpaceIds(batch);
        } catch (Exception e) {
            log.error("知识空间向量回填失败: size={}, error={}", batch.size(), e.getMessage());
        }
    }
}
