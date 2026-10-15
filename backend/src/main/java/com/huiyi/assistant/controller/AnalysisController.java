package com.huiyi.assistant.controller;

import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.model.dto.AnalysisRequestDTO;
import com.huiyi.assistant.model.vo.AnalysisResultVO;
import com.huiyi.assistant.service.AnalysisService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据分析接口（Text-to-SQL）。
 *
 * @author huiyi
 */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 自然语言数据分析：生成 SQL、安全校验、执行、画像与总结。
     *
     * @param request 分析请求
     * @return 分析结果（question/sql/rows/summary/answer）
     */
    @PostMapping("/query")
    public Result<AnalysisResultVO> query(@Validated @RequestBody AnalysisRequestDTO request) {
        return Result.ok(analysisService.query(request));
    }
}
