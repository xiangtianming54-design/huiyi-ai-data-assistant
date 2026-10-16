package com.huiyi.assistant.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 复合问答的单个子问题结果。
 *
 * @author huiyi
 */
@Data
@Builder
public class ChatPartVO {

    /** 子问题（分解后的独立问题） */
    private String question;

    /** 意图编码 */
    private String intent;

    /** 文本回答 */
    private String answer;

    /** 引用来源（知识问答） */
    private List<ReferenceVO> references;

    /** 富结果（数据查询，含 sql/rows/summary） */
    private AnalysisResultVO analysis;
}
