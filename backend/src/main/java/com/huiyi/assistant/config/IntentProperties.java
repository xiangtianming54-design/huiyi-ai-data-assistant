package com.huiyi.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 意图识别规则配置（app.intent.*），关键词外置便于不改代码调优。
 *
 * @author huiyi
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.intent")
public class IntentProperties {

    /** 强数据查询关键词：单独命中即判定 data_query */
    private List<String> strongKeywords = List.of(
            "排名", "销售额", "订单量", "趋势", "最高", "最低", "对比", "占比", "汇总", "统计",
            "top", "TOP", "Top",
            "删除", "修改", "更新", "清空");

    /** 弱数据查询关键词：需与业务名词共现 */
    private List<String> weakKeywords = List.of("多少", "几个", "哪家");

    /** 业务名词：与弱关键词共现时判定 data_query */
    private List<String> businessNouns = List.of(
            "订单", "销售", "商品", "产品", "渠道", "客户", "收入", "销量", "金额");
}
