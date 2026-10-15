package com.huiyi.assistant.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 数据分析结果视图对象（与《开发文档》7.1 节一致，字段统一驼峰）。
 *
 * @author huiyi
 */
@Data
@Builder
public class AnalysisResultVO {

    /** 会话 ID */
    private String sessionId;

    /** 原始问题 */
    private String question;

    /** 生成并执行的 SQL（未生成时为 null） */
    private String sql;

    /** 结果行集（最多 500 行） */
    private List<Map<String, Object>> rows;

    /** 摘要信息 */
    private Summary summary;

    /** 自然语言总结 / 兜底提示 */
    private String answer;

    /**
     * 摘要：行数与图表类型。
     */
    @Data
    @Builder
    public static class Summary {

        /** 行数 */
        private Integer rowCount;

        /** 推荐图表类型 */
        private String chartType;
    }
}
