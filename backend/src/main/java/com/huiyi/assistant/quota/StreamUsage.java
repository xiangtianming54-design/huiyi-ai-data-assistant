package com.huiyi.assistant.quota;

import org.springframework.ai.chat.metadata.Usage;

/**
 * 从一段流式响应的 Usage 取出 prompt 与 completion。任一侧缺失则本段不能记账。
 */
public final class StreamUsage {

    private StreamUsage() {
    }

    /**
     * @return {@code int[]{prompt, completion}}；usage 或其中一侧为 null 时返回 null
     */
    public static int[] from(Usage usage) {
        if (usage == null) {
            return null;
        }
        Integer prompt = usage.getPromptTokens();
        Integer completion = usage.getCompletionTokens();
        if (prompt == null || completion == null) {
            return null;
        }
        return new int[]{prompt, completion};
    }
}
