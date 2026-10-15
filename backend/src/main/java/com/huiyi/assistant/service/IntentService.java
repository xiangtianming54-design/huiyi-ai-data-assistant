package com.huiyi.assistant.service;

import com.huiyi.assistant.config.IntentProperties;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import org.springframework.stereotype.Service;

/**
 * 意图识别 v1（规则版）：强关键词直接判定数据查询；弱关键词需与业务名词共现，否则为知识问答。
 * 关键词规则见 {@link IntentProperties}，可在 application.yml 的 app.intent.* 中调优。
 *
 * @author huiyi
 */
@Service
public class IntentService {

    private final IntentProperties intentProperties;

    public IntentService(IntentProperties intentProperties) {
        this.intentProperties = intentProperties;
    }

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
        if (intentProperties.getStrongKeywords().stream().anyMatch(question::contains)) {
            return IntentTypeEnum.DATA_QUERY;
        }
        boolean hasWeakKeyword = intentProperties.getWeakKeywords().stream().anyMatch(question::contains);
        boolean hasBusinessNoun = intentProperties.getBusinessNouns().stream().anyMatch(question::contains);
        if (hasWeakKeyword && hasBusinessNoun) {
            return IntentTypeEnum.DATA_QUERY;
        }
        return IntentTypeEnum.KNOWLEDGE_QA;
    }
}
