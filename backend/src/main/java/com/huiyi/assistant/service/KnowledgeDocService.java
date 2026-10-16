package com.huiyi.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huiyi.assistant.audit.AuditAction;
import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.ResultCode;
import com.huiyi.assistant.mapper.KnowledgeDocMapper;
import com.huiyi.assistant.model.dto.ParseResultDTO;
import com.huiyi.assistant.model.entity.KnowledgeDoc;
import com.huiyi.assistant.model.enums.DocStatusEnum;
import com.huiyi.assistant.model.vo.KnowledgeDocVO;
import com.huiyi.assistant.model.vo.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class KnowledgeDocService {

    private final PythonServiceClient pythonServiceClient;
    private final KnowledgeDocMapper knowledgeDocMapper;
    private final AuditService auditService;

    public KnowledgeDocService(PythonServiceClient pythonServiceClient, KnowledgeDocMapper knowledgeDocMapper,
                               AuditService auditService) {
        this.pythonServiceClient = pythonServiceClient;
        this.knowledgeDocMapper = knowledgeDocMapper;
        this.auditService = auditService;
    }

    public KnowledgeDocVO upload(MultipartFile file) {
        return upload(file, null, null);
    }

    public KnowledgeDocVO upload(MultipartFile file, Long workspaceId, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR.getCode(), "上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        log.info("开始处理上传文档: {}, size={}", filename, file.getSize());

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDocName(filename);
        doc.setFileType(extractFileType(filename));
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus(DocStatusEnum.UPLOADED.name());
        doc.setWorkspaceId(workspaceId);
        doc.setCreatedBy(userId);
        knowledgeDocMapper.insert(doc);

        try {
            ParseResultDTO parseResult = pythonServiceClient.parseDocument(file);
            if (parseResult == null) {
                throw new BizException(ResultCode.PYTHON_SERVICE_UNAVAILABLE);
            }
            pythonServiceClient.upsertVectors(parseResult, workspaceId, doc.getId());
            doc.setDocName(parseResult.getFilename());
            doc.setChunkCount(parseResult.getChunkCount());
            doc.setStatus(DocStatusEnum.READY.name());
            knowledgeDocMapper.updateById(doc);
            auditService.record(AuditAction.KNOWLEDGE_UPLOADED, "SUCCESS", userId, null, workspaceId,
                    "knowledge_doc", String.valueOf(doc.getId()), Map.of("filename", filename == null ? "" : filename));
            log.info("文档已就绪: docId={}, chunkCount={}", doc.getId(), doc.getChunkCount());
            return toVO(doc);
        } catch (RuntimeException e) {
            doc.setStatus(DocStatusEnum.FAILED.name());
            knowledgeDocMapper.updateById(doc);
            throw e;
        }
    }

    public List<KnowledgeDocVO> listDocs() {
        return listDocs(null, 1, 100).getItems();
    }

    public List<KnowledgeDocVO> listDocs(Long workspaceId) {
        return listDocs(workspaceId, 1, 100).getItems();
    }

    public PageResult<KnowledgeDocVO> listDocs(Long workspaceId, Integer page, Integer pageSize) {
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<KnowledgeDoc>()
                .ne(KnowledgeDoc::getStatus, DocStatusEnum.ARCHIVED.name())
                .orderByDesc(KnowledgeDoc::getCreatedAt);
        if (workspaceId != null) {
            wrapper.eq(KnowledgeDoc::getWorkspaceId, workspaceId);
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
