package com.huiyi.assistant.quota;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.Usage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * StreamUsage 把一段流式 Usage 收成 prompt、completion；缺任一侧则不能记账。
 */
class StreamUsageTest {

    @Test
    void nullUsageIsNull() {
        assertNull(StreamUsage.from(null));
    }

    @Test
    void bothSidesReturnPromptThenCompletion() {
        assertArrayEquals(new int[]{7, 11}, StreamUsage.from(usage(7, 11)));
    }

    @Test
    void eitherSideNullIsNull() {
        assertNull(StreamUsage.from(usage(7, null)));
        assertNull(StreamUsage.from(usage(null, 11)));
    }

    private static Usage usage(Integer prompt, Integer completion) {
        return new Usage() {
            @Override
            public Integer getPromptTokens() {
                return prompt;
            }

            @Override
            public Integer getCompletionTokens() {
                return completion;
            }

            @Override
            public Object getNativeUsage() {
                return null;
            }
        };
    }
}
