package com.huiyi.assistant.service;

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

/**
 * 知识库文档服务：上传解析、元数据管理。
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
     * 上传文档：转发 Python 解析分块，成功后保存元数据（status=PARSED）。
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

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDocName(parseResult.getFilename());
        doc.setFileType(extractFileType(filename));
        doc.setFileSize(file.getSize());
        doc.setChunkCount(parseResult.getChunkCount());
        doc.setStatus(DocStatusEnum.PARSED.name());
        knowledgeDocMapper.insert(doc);
        log.info("文档元数据已落库: docId={}, chunkCount={}", doc.getId(), doc.getChunkCount());

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
