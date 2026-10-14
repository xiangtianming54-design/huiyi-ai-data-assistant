package com.huiyi.assistant.controller;

import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.model.vo.KnowledgeDocVO;
import com.huiyi.assistant.service.KnowledgeDocService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理接口。
 *
 * @author huiyi
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeDocService knowledgeDocService;

    public KnowledgeController(KnowledgeDocService knowledgeDocService) {
        this.knowledgeDocService = knowledgeDocService;
    }

    /**
     * 上传知识文档：解析分块并保存元数据。
     *
     * @param file 文档文件（md/txt/pdf/docx）
     * @return 文档元数据
     */
    @PostMapping("/upload")
    public Result<KnowledgeDocVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(knowledgeDocService.upload(file));
    }
}
