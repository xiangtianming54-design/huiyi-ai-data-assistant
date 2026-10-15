package com.huiyi.assistant.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 引用来源视图对象。
 *
 * @author huiyi
 */
@Data
@AllArgsConstructor
public class ReferenceVO {

    /** 来源文件名 */
    private String source;

    /** 相似度分数 */
    private Double score;
}
