package com.huiyi.assistant.agent;

public final class ToolSchemas {

    public static final String KNOWLEDGE_SEARCH = """
            {"type":"object","additionalProperties":false,"properties":{"query":{"type":"string","description":"可选；缺省用当前问题"}}}
            """;

    public static final String SQL_GENERATE = """
            {"type":"object","additionalProperties":false,"properties":{}}
            """;

    public static final String SQL_VALIDATE = """
            {"type":"object","additionalProperties":false,"required":["sql"],"properties":{"sql":{"type":"string","minLength":1}}}
            """;

    public static final String SQL_EXECUTE_READONLY = SQL_VALIDATE;

    public static final String DATA_PROFILE = """
            {"type":"object","additionalProperties":false,"properties":{"rows":{"type":"array","items":{"type":"object"}}}}
            """;

    private ToolSchemas() {
    }
}
