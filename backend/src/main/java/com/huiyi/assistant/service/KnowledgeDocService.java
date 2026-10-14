package com.huiyi.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.ResultCode;
import com.huiyi.assistant.mapper.KnowledgeDocMapper;
import com.huiyi.assistant.model.dto.ParseResultDTO;
import com.huiyi.assistant.model.entity.KnowledgeDoc;
import com.huiyi.assistant.model.enums.DocStatusEnum;
import com.huiyi.assistant.model.vo.KnowledgeDocVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档服务：上传解析、向量写入、元数据管理、删除。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class KnowledgeDocService {

    private final PythonServiceClient pythonServiceClient;

    private final KnowledgeDocMapper knowledgeDocMapper;

    public KnowledgeDocService(PythonServiceClient pythonServiceClient, KnowledgeDocMapper knowledgeDocMapper) {
        this.pythonServiceClient = pythonServiceClient;
        this.knowledgeDocMapper = knowledgeDocMapper;
    }

    /**
     * 上传文档：解析分块 → 写入向量库 → 保存元数据（status=READY）。
     *
     * @param file 上传文件
     * @return 文档视图对象
     */
    public KnowledgeDocVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR.getCode(), "上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        log.info("开始处理上传文档: {}, size={}", filename, file.getSize());

        ParseResultDTO parseResult = pythonServiceClient.parseDocument(file);
        if (parseResult == null) {
            throw new BizException(ResultCode.PYTHON_SERVICE_UNAVAILABLE);
        }

        // 写入向量库，成功后文档才进入可检索状态
        pythonServiceClient.upsertVectors(parseResult);

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDocName(parseResult.getFilename());
        doc.setFileType(extractFileType(filename));
        doc.setFileSize(file.getSize());
        doc.setChunkCount(parseResult.getChunkCount());
        doc.setStatus(DocStatusEnum.READY.name());
        knowledgeDocMapper.insert(doc);
        log.info("文档已就绪: docId={}, chunkCount={}", doc.getId(), doc.getChunkCount());

        return toVO(doc);
    }

    /**
     * 文档列表（按创建时间倒序）。
     *
     * @return 文档视图对象列表
     */
    public List<KnowledgeDocVO> listDocs() {
        List<KnowledgeDoc> docs = knowledgeDocMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDoc>().orderByDesc(KnowledgeDoc::getCreatedAt));
        return docs.stream().map(this::toVO).toList();
    }

    /**
     * 删除文档：向量与元数据双删。
     *
     * @param docId 文档 ID
     */
    public void deleteDoc(Long docId) {
        KnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
        if (doc == null) {
            throw new BizException(ResultCode.PARAM_ERROR.getCode(), "文档不存在: " + docId);
        }
        pythonServiceClient.deleteVectorsByFilename(doc.getDocName());
        knowledgeDocMapper.deleteById(docId);
        log.info("文档已删除: docId={}, docName={}", docId, doc.getDocName());
    }

    /**
     * 实体转视图对象。
     */
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

    /**
     * 提取文件扩展名（小写，不含点）。
     */
    private String extractFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
