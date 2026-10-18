package com.huiyi.assistant.agent;

import java.util.regex.Pattern;

public final class OutputGuard {

    static final String GENERIC_FAILURE = "AI 服务暂不可用，请稍后重试。";

    private static final Pattern TAINT = Pattern.compile(
            "Exception in thread[^\\n]*"
                    + "|Traceback \\(most recent call last\\)[^\\n]*"
                    + "|Caused by:[^\\n]*"
                    + "|sk-[A-Za-z0-9]{8,}"
                    + "|API_KEY=\\S*"
                    + "|\\bat (?:com|org|java|javax|jakarta|sun|jdk)\\.[^\\n]*"
                    + "|[\\w$.]+(?:Exception|Error)(?::[^\\n]*)?",
            Pattern.MULTILINE);

    public String sanitize(String text) {
        if (text == null) {
            return null;
        }
        if (AgentRunner.UNKNOWN_TOOL_USER_MESSAGE.equals(text)
                || AgentRunner.LIMIT_USER_MESSAGE.equals(text)) {
            return text;
        }
        if (!containsTaint(text)) {
            return text;
        }
        String stripped = TAINT.matcher(text).replaceAll("");
        String cleaned = stripped.replaceAll("[\\t ]{2,}", " ").replaceAll("\\n{2,}", "\n").trim();
        if (cleaned.isEmpty() || containsTaint(cleaned)) {
            return GENERIC_FAILURE;
        }
        return cleaned;
    }

    private static boolean containsTaint(String text) {
        return text.contains("Exception in thread")
                || text.contains("Traceback (most recent call last)")
                || text.contains("API_KEY=")
                || TAINT.matcher(text).find();
    }
}
