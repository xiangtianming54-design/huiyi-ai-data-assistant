package com.huiyi.assistant.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 统一问答响应（支持复合问题的多部分回答）。
 *
 * @author huiyi
 */
@Data
@Builder
public class ChatAskResponseVO {

    /** 会话 ID */
    private String sessionId;

    /** 子问题结果列表（单问题时为 1 个） */
    private List<ChatPartVO> parts;
}
