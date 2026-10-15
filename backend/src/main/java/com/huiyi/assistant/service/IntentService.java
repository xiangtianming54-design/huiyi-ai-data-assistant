package com.huiyi.assistant.service;

import com.huiyi.assistant.model.enums.IntentTypeEnum;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 意图识别 v1（规则版）：强关键词直接判定数据查询；弱关键词需与业务名词共现，否则为知识问答。
 *
 * @author huiyi
 */
@Service
public class IntentService {

    /** 强数据查询关键词：单独命中即判定 data_query */
    private static final List<String> STRONG_DATA_KEYWORDS = List.of(
            "排名", "销售额", "订单量", "趋势", "最高", "最低", "对比", "占比", "汇总", "统计",
            "top", "TOP", "Top",
            // 数据修改动词：路由到数据链路后由 SQL 安全防线拦截（场景 4）
            "删除", "修改", "更新", "清空");

    /** 弱数据查询关键词：需与业务名词共现（避免「年假有多少天」误判） */
    private static final List<String> WEAK_DATA_KEYWORDS = List.of("多少", "几个", "哪家");

    /** 业务名词：与弱关键词共现时判定 data_query */
    private static final List<String> BUSINESS_NOUNS = List.of(
            "订单", "销售", "商品", "产品", "渠道", "客户", "收入", "销量", "金额");

    /**
     * 识别问题意图。
     *
     * @param question 用户问题
     * @return 意图类型
     */
    public IntentTypeEnum detect(String question) {
        if (question == null || question.isBlank()) {
            return IntentTypeEnum.KNOWLEDGE_QA;
        }
        if (STRONG_DATA_KEYWORDS.stream().anyMatch(question::contains)) {
            return IntentTypeEnum.DATA_QUERY;
        }
        boolean hasWeakKeyword = WEAK_DATA_KEYWORDS.stream().anyMatch(question::contains);
        boolean hasBusinessNoun = BUSINESS_NOUNS.stream().anyMatch(question::contains);
        if (hasWeakKeyword && hasBusinessNoun) {
            return IntentTypeEnum.DATA_QUERY;
        }
        return IntentTypeEnum.KNOWLEDGE_QA;
    }
}
