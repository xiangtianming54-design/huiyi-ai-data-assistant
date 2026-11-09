package com.huiyi.assistant.quota;

/**
 * 本地 Token 估算。不访问网络，也不调用模型。
 * ASCII 连续段每 4 个字符计 1，不足 4 进 1；每个非 ASCII 码位计 1；空白只断开 ASCII 段。
 *
 * @author huiyi
 */
public final class LocalTokenCounter {

    private LocalTokenCounter() {
    }

    /**
     * 估算文本的 Token 数。{@code null} 和空串为 0，同一字符串每次结果相同。
     *
     * @param text 待估算文本
     * @return Token 数
     */
    public static int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int tokens = 0;
        int asciiRun = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint <= 0x7F && !Character.isWhitespace(codePoint)) {
                asciiRun++;
                continue;
            }
            tokens += tokensForAsciiRun(asciiRun);
            asciiRun = 0;
            if (codePoint > 0x7F) {
                tokens++;
            }
        }
        return tokens + tokensForAsciiRun(asciiRun);
    }

    /** 连续非空白 ASCII 的长度换成 Token：每 4 个 1 个，余数进 1。 */
    private static int tokensForAsciiRun(int length) {
        if (length == 0) {
            return 0;
        }
        return (length + 3) / 4;
    }
}
