package com.huiyi.assistant.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 问答响应视图对象（与《开发文档》7.1 节一致）。
 *
 * @author huiyi
 */
@Data
@Builder
public class ChatResponseVO {

    /** 会话 ID */
    private String sessionId;

    /** 回答 */
    private String answer;

    /** 意图编码 */
    private String intent;

    /** 引用来源 */
    private List<ReferenceVO> references;
}
