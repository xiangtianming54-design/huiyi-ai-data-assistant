package com.huiyi.assistant.agent;

public final class ContextBudget {

    private final int maxChars;

    public ContextBudget() {
        this(24_000);
    }

    public ContextBudget(int maxChars) {
        this.maxChars = Math.max(1, maxChars);
    }

    public String trimKnowledge(String knowledge) {
        return cap(knowledge, maxChars / 2);
    }

    public String trimSchema(String schema) {
        return cap(schema, maxChars / 2);
    }

    public String trimHistory(String history) {
        return cap(history, maxChars / 2);
    }

    public void assertFits(String system, String knowledge, String schema, String history, String question) {
        String sys = nullToEmpty(system);
        String q = nullToEmpty(question);
        String k = nullToEmpty(knowledge);
        String s = nullToEmpty(schema);
        String h = nullToEmpty(history);
        k = fitRemaining(k, sys.length() + q.length() + s.length() + h.length());
        s = fitRemaining(s, sys.length() + q.length() + k.length() + h.length());
        h = fitRemaining(h, sys.length() + q.length() + k.length() + s.length());
        if (sys.length() + q.length() + k.length() + s.length() + h.length() > maxChars) {
            throw new PromptBudgetExceededException();
        }
    }

    private String fitRemaining(String value, int others) {
        int remain = maxChars - others;
        return cap(value, Math.max(0, remain));
    }

    private static String cap(String value, int max) {
        String text = nullToEmpty(value);
        if (max <= 0) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
