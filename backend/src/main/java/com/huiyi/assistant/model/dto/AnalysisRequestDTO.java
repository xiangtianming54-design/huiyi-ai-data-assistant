package com.huiyi.assistant.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数据分析请求。
 *
 * @author huiyi
 */
@Data
public class AnalysisRequestDTO {

    /** 会话 ID：为空时由服务端生成 */
    private String sessionId;

    /** 数据分析问题 */
    @NotBlank(message = "问题不能为空")
    private String question;
}
