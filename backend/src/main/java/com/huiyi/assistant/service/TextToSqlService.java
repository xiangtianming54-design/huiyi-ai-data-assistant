package com.huiyi.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.assistant.common.util.PromptTemplateLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text-to-SQL 服务：Prompt 拼装 → LLM 生成 → SQL 提取（JSON 优先，正则兜底，不抛栈）。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class TextToSqlService {

    /** Text-to-SQL Prompt 模板位置 */
    private static final String TEXT_TO_SQL_PROMPT = "prompts/text-to-sql-system.md";

    /** 业务口径文本位置 */
    private static final String BUSINESS_GLOSSARY = "prompts/business-glossary.txt";

    /** Markdown 代码块中的 SQL */
    private static final Pattern SQL_CODE_BLOCK = Pattern.compile("```(?:sql)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /** 裸 SELECT 语句 */
    private static final Pattern BARE_SELECT = Pattern.compile("(?is)(select\\s[\\s\\S]*?)(;|\\s*```|\\s*$)");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LlmService llmService;

    private final SchemaService schemaService;

    private final PromptTemplateLoader promptTemplateLoader;

    public TextToSqlService(LlmService llmService, SchemaService schemaService,
                            PromptTemplateLoader promptTemplateLoader) {
        this.llmService = llmService;
        this.schemaService = schemaService;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    /**
     * 生成 SQL。
     *
     * @param question 用户问题
     * @return 提取出的 SQL；无法理解问题或提取失败时返回 null（不抛栈）
     */
    public String generateSql(String question) {
        String prompt = promptTemplateLoader.render(TEXT_TO_SQL_PROMPT, Map.of(
                "schema", schemaService.getSchemaDescription(),
                "business_glossary", promptTemplateLoader.render(BUSINESS_GLOSSARY, Map.of()),
                "question", question));
        String output = llmService.chat(prompt);
        log.info("LLM 原始输出: {}", output);
        return extractSql(output);
    }

    /**
     * 从 LLM 输出提取 SQL：JSON → ```sql 代码块 → 裸 SELECT，三级兜底。
     */
    String extractSql(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        // 1. JSON 解析（可能包裹在 ```json 代码块中）
        String sql = extractFromJson(output.trim());
        if (sql != null) {
            return sql;
        }
        // 2. Markdown 代码块
        Matcher codeBlockMatcher = SQL_CODE_BLOCK.matcher(output);
        if (codeBlockMatcher.find()) {
            String candidate = codeBlockMatcher.group(1).trim();
            // 代码块内可能仍是 JSON
            String jsonSql = extractFromJson(candidate);
            if (jsonSql != null) {
                return jsonSql;
            }
            if (candidate.toUpperCase().startsWith("SELECT")) {
                return candidate;
            }
        }
        // 3. 裸 SELECT 语句
        Matcher selectMatcher = BARE_SELECT.matcher(output);
        if (selectMatcher.find()) {
            return selectMatcher.group(1).trim();
        }
        log.warn("无法从 LLM 输出提取 SQL: {}", output);
        return null;
    }

    /**
     * 尝试按 JSON 解析提取 sql 字段。
     */
    private String extractFromJson(String text) {
        String json = text;
        if (!json.startsWith("{")) {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            json = json.substring(start, end + 1);
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            String sql = node.path("sql").asText();
            return sql.isBlank() ? null : sql.trim();
        } catch (Exception e) {
            return null;
        }
    }
}
