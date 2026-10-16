package com.huiyi.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 意图识别配置（app.intent.*）。
 * mode=rule 时仅规则版（v1 行为）；mode=hybrid 时启用 规则→embedding→LLM 三层判定。
 *
 * @author huiyi
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.intent")
public class IntentProperties {

    /** 判定模式：rule（纯规则）/ hybrid（三层混合） */
    private String mode = "hybrid";

    /** 强数据查询关键词：单独命中即判定 data_query（第一层快路径） */
    private List<String> strongKeywords = List.of(
            "排名", "销售额", "订单量", "趋势", "最高", "最低", "对比", "占比", "汇总", "统计",
            "top", "TOP", "Top",
            "删除", "修改", "更新", "清空");

    /** 弱数据查询关键词：需与业务名词共现（第一层快路径） */
    private List<String> weakKeywords = List.of("多少", "几个", "哪家");

    /** 业务名词：与弱关键词共现时判定 data_query */
    private List<String> businessNouns = List.of(
            "订单", "销售", "商品", "产品", "渠道", "客户", "收入", "销量", "金额");

    /** 知识问答关键词：定义/口径类问法的快路径（第一层） */
    private List<String> knowledgeKeywords = List.of(
            "什么是", "是什么", "定义", "口径", "什么意思", "有哪些", "包含哪些");

    /** embedding 投票置信阈值：最高分低于该值时转 LLM 兜底 */
    private Double embeddingThreshold = 0.55;

    /** 意图示例句（embedding 投票用） */
    private Examples examples = new Examples();

    /**
     * 两类意图的示例句。
     */
    @Data
    public static class Examples {

        /** 数据查询类示例 */
        private List<String> dataQuery = List.of(
                "上个月销售额最高的商品排名",
                "各渠道订单量对比分析",
                "最近三个月销售趋势怎么样",
                "哪个地区的销量最高",
                "统计一下本月有多少笔订单",
                "各商品类别的销售额占比",
                "销量前十的商品有哪些",
                "华东地区的订单总金额是多少");

        /** 知识问答类示例 */
        private List<String> knowledgeQa = List.of(
                "什么是有效订单？",
                "销售额的统计口径是什么？",
                "有效订单的定义是什么？",
                "订单状态都有哪些，分别是什么意思？",
                "公司有哪些销售渠道？",
                "华东地区包含哪些省份？",
                "退款订单算不算有效订单？",
                "业务上怎么定义销售额？");
    }
}
