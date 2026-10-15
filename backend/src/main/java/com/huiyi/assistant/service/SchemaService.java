package com.huiyi.assistant.service;

import com.huiyi.assistant.config.SqlProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Schema 服务：读取 information_schema 生成白名单表的建表描述，缓存 10 分钟。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class SchemaService {

    /** Schema 文本缓存时长（毫秒） */
    private static final long CACHE_MS = 10 * 60 * 1000L;

    private final JdbcTemplate jdbcTemplate;

    private final SqlProperties sqlProperties;

    private volatile String cachedSchema;

    private volatile long cacheTime;

    public SchemaService(JdbcTemplate jdbcTemplate, SqlProperties sqlProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlProperties = sqlProperties;
    }

    /**
     * 获取白名单表的结构描述文本（10 分钟缓存）。
     *
     * @return 形如「表 sales_orders（销售订单表）:\n  - id: BIGINT\n ...」的描述
     */
    public String getSchemaDescription() {
        long now = System.currentTimeMillis();
        if (cachedSchema != null && now - cacheTime < CACHE_MS) {
            return cachedSchema;
        }
        synchronized (this) {
            if (cachedSchema != null && now - cacheTime < CACHE_MS) {
                return cachedSchema;
            }
            cachedSchema = buildSchemaDescription();
            cacheTime = System.currentTimeMillis();
            log.info("Schema 描述已刷新，长度={}", cachedSchema.length());
            return cachedSchema;
        }
    }

    /**
     * 从 information_schema 构建表结构描述。
     */
    private String buildSchemaDescription() {
        List<String> tables = sqlProperties.getAllowedTables();
        String placeholders = tables.stream().map(t -> "?").collect(Collectors.joining(","));

        Map<String, String> tableComments = new TreeMap<>();
        List<Map<String, Object>> tableRows = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (" + placeholders + ")",
                tables.toArray());
        for (Map<String, Object> row : tableRows) {
            tableComments.put((String) row.get("TABLE_NAME"), (String) row.get("TABLE_COMMENT"));
        }

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (" + placeholders + ") "
                        + "ORDER BY TABLE_NAME, ORDINAL_POSITION",
                tables.toArray());

        Map<String, List<Map<String, Object>>> columnsByTable = columns.stream()
                .collect(Collectors.groupingBy(row -> (String) row.get("TABLE_NAME"),
                        TreeMap::new, Collectors.toList()));

        StringBuilder schema = new StringBuilder();
        for (String table : tables) {
            List<Map<String, Object>> tableColumns = columnsByTable.get(table);
            if (tableColumns == null) {
                log.warn("白名单表在数据库中不存在: {}", table);
                continue;
            }
            schema.append("表 ").append(table);
            String comment = tableComments.getOrDefault(table, "");
            if (comment != null && !comment.isBlank()) {
                schema.append("（").append(comment).append("）");
            }
            schema.append(":\n");
            for (Map<String, Object> column : tableColumns) {
                schema.append("  - ").append(column.get("COLUMN_NAME"))
                        .append(": ").append(column.get("COLUMN_TYPE"));
                String columnComment = (String) column.get("COLUMN_COMMENT");
                if (columnComment != null && !columnComment.isBlank()) {
                    schema.append("，").append(columnComment);
                }
                schema.append("\n");
            }
        }
        return schema.toString().trim();
    }
}
