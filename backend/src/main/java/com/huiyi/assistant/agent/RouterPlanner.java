package com.huiyi.assistant.agent;

import com.huiyi.assistant.model.enums.IntentTypeEnum;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RouterPlanner {

    public List<String> toolsFor(IntentTypeEnum intent) {
        if (intent == IntentTypeEnum.DATA_QUERY) {
            return List.of(
                    ToolNames.SQL_GENERATE,
                    ToolNames.SQL_VALIDATE,
                    ToolNames.SQL_EXECUTE_READONLY,
                    ToolNames.DATA_PROFILE);
        }
        return List.of(ToolNames.KNOWLEDGE_SEARCH);
    }
}
