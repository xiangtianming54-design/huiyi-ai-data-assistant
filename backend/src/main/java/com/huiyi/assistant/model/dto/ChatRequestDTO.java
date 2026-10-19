package com.huiyi.assistant.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 问答请求。
 *
 * @author huiyi
 */
@Data
public class ChatRequestDTO {

    /** 会话 ID：为空时由服务端生成 */
    private String sessionId;

    /** 用户问题 */
    @NotBlank(message = "问题不能为空")
    private String question;

    /** 可选：收窄知识检索的空间 id */
    private List<Long> spaceIds;
}
