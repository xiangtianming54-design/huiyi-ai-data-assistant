package com.huiyi.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huiyi.assistant.audit.AuditAction;
import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.knowledge.AsyncTaskService;
import com.huiyi.assistant.knowledge.DocParseJob;
import com.huiyi.assistant.knowledge.KnowledgeSpaceService;
import com.huiyi.assistant.knowledge.LocalFileStorageClient;
import com.huiyi.assistant.common.result.ResultCode;
import com.huiyi.assistant.mapper.AsyncTaskMapper;
import com.huiyi.assistant.mapper.KnowledgeDocMapper;
import com.huiyi.assistant.model.entity.AsyncTask;
import com.huiyi.assistant.model.entity.KnowledgeDoc;
import com.huiyi.assistant.model.enums.DocStatusEnum;
import com.huiyi.assistant.model.vo.KnowledgeDocVO;
import com.huiyi.assistant.model.vo.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
public class KnowledgeDocService {

    private final PythonServiceClient pythonServiceClient;
    private final KnowledgeDocMapper knowledgeDocMapper;
    private final AuditService auditService;
    private final KnowledgeSpaceService knowledgeSpaceService;
    private final LocalFileStorageClient storage;
    private final AsyncTaskService asyncTasks;
    private final AsyncTaskMapper asyncTaskMapper;
    private final TaskExecutor knowledgeParseExecutor;

    public KnowledgeDocService(PythonServiceClient pythonServiceClient, KnowledgeDocMapper knowledgeDocMapper,
                               AuditService auditService, KnowledgeSpaceService knowledgeSpaceService,
                               LocalFileStorageClient storage, AsyncTaskService asyncTasks,
                               AsyncTaskMapper asyncTaskMapper,
                               @Qualifier("knowledgeParseExecutor") TaskExecutor knowledgeParseExecutor) {
        this.pythonServiceClient = pythonServiceClient;
        this.knowledgeDocMapper = knowledgeDocMapper;
        this.auditService = auditService;
        this.knowledgeSpaceService = knowledgeSpaceService;
        this.storage = storage;
        this.asyncTasks = asyncTasks;
        this.asyncTaskMapper = asyncTaskMapper;
        this.knowledgeParseExecutor = knowledgeParseExecutor;
    }

    public KnowledgeDocVO upload(MultipartFile file) {
        return upload(file, null, null);
    }

    public KnowledgeDocVO upload(MultipartFile file, Long workspaceId, Long userId) {
        return upload(file, workspaceId, userId, null);
    }

    public KnowledgeDocVO upload(MultipartFile file, Long workspaceId, Long userId, Long spaceId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR.getCode(), "上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        log.info("开始处理上传文档: {}, size={}", filename, file.getSize());

        Long resolvedSpaceId = null;
        if (workspaceId != null) {
            resolvedSpaceId = knowledgeSpaceService.requireActiveSpace(workspaceId, spaceId).getId();
        }

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDocName(filename);
        doc.setFileType(extractFileType(filename));
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus(DocStatusEnum.UPLOADED.name());
        doc.setWorkspaceId(workspaceId);
        doc.setKnowledgeSpaceId(resolvedSpaceId);
        doc.setCreatedBy(userId);
        knowledgeDocMapper.insert(doc);

        try {
            byte[] content = file.getBytes();
            String key = storage.save(workspaceId, doc.getId(), content, filename);
            doc.setStorageKey(key);
            knowledgeDocMapper.updateById(doc);
        } catch (IOException | RuntimeException e) {
            doc.setStatus(DocStatusEnum.FAILED.name());
            doc.setErrorMessage("保存上传文件失败");
            knowledgeDocMapper.updateById(doc);
            throw new BizException(500, "保存上传文件失败");
        }

        AsyncTask task = asyncTasks.enqueueDocParse(workspaceId, userId, doc.getId());
        try {
            knowledgeParseExecutor.execute(
                    new DocParseJob(task, knowledgeDocMapper, asyncTaskMapper, storage, pythonServiceClient));
        } catch (RejectedExecutionException e) {
            doc.setStatus(DocStatusEnum.FAILED.name());
            doc.setErrorMessage("解析任务繁忙");
            knowledgeDocMapper.updateById(doc);
            asyncTasks.fail(task, "解析任务繁忙");
            return toVO(doc);
        }
        auditService.record(AuditAction.KNOWLEDGE_UPLOADED, "SUCCESS", userId, null, workspaceId,
                "knowledge_doc", String.valueOf(doc.getId()), Map.of("filename", filename == null ? "" : filename));
        log.info("文档已入队: docId={}", doc.getId());
        return toVO(doc);
    }

    public List<KnowledgeDocVO> listDocs() {
        return listDocs(null, 1, 100).getItems();
    }

    public List<KnowledgeDocVO> listDocs(Long workspaceId) {
        return listDocs(workspaceId, 1, 100).getItems();
    }

    public PageResult<KnowledgeDocVO> listDocs(Long workspaceId, Integer page, Integer pageSize) {
        return listDocs(workspaceId, page, pageSize, null);
    }

    public PageResult<KnowledgeDocVO> listDocs(Long workspaceId, Integer page, Integer pageSize, Long spaceId) {
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<KnowledgeDoc>()
                .ne(KnowledgeDoc::getStatus, DocStatusEnum.ARCHIVED.name())
                .orderByDesc(KnowledgeDoc::getCreatedAt);
        if (workspaceId != null) {
            wrapper.eq(KnowledgeDoc::getWorkspaceId, workspaceId);
            List<Long> activeIds = knowledgeSpaceService.listActiveIds(workspaceId);
            if (spaceId != null) {
                if (!activeIds.contains(spaceId)) {
                    return PageResult.of(List.of(), page, pageSize);
                }
                wrapper.eq(KnowledgeDoc::getKnowledgeSpaceId, spaceId);
            } else if (activeIds.isEmpty()) {
                return PageResult.of(List.of(), page, pageSize);
            } else {
                wrapper.in(KnowledgeDoc::getKnowledgeSpaceId, activeIds);
            }
        }
        return PageResult.of(knowledgeDocMapper.selectList(wrapper).stream().map(this::toVO).toList(), page, pageSize);
    }

    public KnowledgeDocVO getDoc(Long docId, Long workspaceId) {
        KnowledgeDoc doc = requireDoc(docId, workspaceId);
        return toVO(doc);
    }

    public void deleteDoc(Long docId) {
        archiveDoc(docId, null, null);
    }

    public void archiveDoc(Long docId, Long workspaceId, Long userId) {
        KnowledgeDoc doc = requireDoc(docId, workspaceId);
        if (workspaceId != null && doc.getWorkspaceId() != null && !workspaceId.equals(doc.getWorkspaceId())) {
            throw new BizException(403, "无权归档其他工作区的文档");
        }
        if (doc.getWorkspaceId() != null) {
            pythonServiceClient.deleteVectorsByDoc(doc.getWorkspaceId(), doc.getId());
        }
        doc.setStatus(DocStatusEnum.ARCHIVED.name());
        knowledgeDocMapper.updateById(doc);
        auditService.record(AuditAction.KNOWLEDGE_ARCHIVED, "SUCCESS", userId, null, workspaceId,
                "knowledge_doc", String.valueOf(docId), Map.of());
        log.info("文档已归档: docId={}, docName={}, by={}", docId, doc.getDocName(), userId);
    }

    private KnowledgeDoc requireDoc(Long docId, Long workspaceId) {
        KnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
        if (doc == null) {
            throw new BizException(ResultCode.PARAM_ERROR.getCode(), "文档不存在: " + docId);
        }
        if (workspaceId != null && doc.getWorkspaceId() != null && !workspaceId.equals(doc.getWorkspaceId())) {
            throw new BizException(403, "无权访问其他工作区的文档");
        }
        return doc;
    }

    private KnowledgeDocVO toVO(KnowledgeDoc doc) {
        return KnowledgeDocVO.builder()
                .docId(doc.getId())
                .docName(doc.getDocName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .chunkCount(doc.getChunkCount())
                .status(doc.getStatus())
                .knowledgeSpaceId(doc.getKnowledgeSpaceId())
                .errorMessage(doc.getErrorMessage())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    private String extractFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
