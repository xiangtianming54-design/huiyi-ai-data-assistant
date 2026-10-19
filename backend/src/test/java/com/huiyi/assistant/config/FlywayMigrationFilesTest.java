package com.huiyi.assistant.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验 v0.7 Flyway 脚本覆盖企业基础表，且 init_mysql.sql 不再创建应用表。
 */
class FlywayMigrationFilesTest {

    private static final Path MODULE = Path.of("src/main/resources/db/migration");

    @Test
    void migrationsCreateEnterpriseTables() throws IOException {
        String sql = readAllMigrations();
        for (String table : List.of(
                "knowledge_docs", "chat_messages", "chat_session_summaries",
                "users", "workspaces", "workspace_members", "refresh_tokens",
                "chat_sessions", "audit_events",
                "assistant_runs", "assistant_run_steps", "llm_calls")) {
            assertTrue(sql.contains(table), "missing table " + table);
        }
        assertTrue(sql.contains("tool_name"), "V10 must add assistant_run_steps.tool_name");
        assertTrue(sql.contains("error_code"), "V11 must add assistant_runs.error_code");
        assertTrue(sql.contains("knowledge_spaces"), "V12 must create knowledge_spaces");
        assertFalse(sql.contains("async_tasks"), "no async_tasks table");
        assertFalse(sql.contains("FOREIGN KEY"), "no MySQL FK");
        assertFalse(sql.contains("arguments_json"), "no steps JSON columns");
        assertFalse(sql.contains("CREATE TABLE IF NOT EXISTS agent_runs")
                || sql.contains("CREATE TABLE agent_runs"), "no agent_runs table");
        assertFalse(sql.contains("tool_invocations"), "no tool_invocations table");
        assertFalse(sql.contains("tool_registry"), "no tool_registry table");
        assertTrue(sql.contains("username = 'migrated'") || sql.contains("username,'migrated'")
                || sql.contains("'migrated'"), "missing migrated placeholder user");
        assertTrue(sql.contains("'default'"), "missing default workspace code");
    }

    @Test
    void flywayBaselinesExistingNonEmptySchema() throws IOException {
        for (String file : List.of(
                "src/main/resources/application-local.yml",
                "src/main/resources/application-docker.yml")) {
            String yaml = Files.readString(Path.of(file), StandardCharsets.UTF_8);
            assertTrue(yaml.contains("baseline-on-migrate: true"), file + " must baseline existing demo schema");
            assertTrue(yaml.contains("baseline-version: 0"), file + " must still run V1 on demo schema");
        }
    }

    @Test
    void initMysqlNoLongerCreatesApplicationTables() throws IOException {
        String init = Files.readString(Path.of("../scripts/init_mysql.sql"), StandardCharsets.UTF_8);
        assertFalse(init.contains("CREATE TABLE IF NOT EXISTS knowledge_docs"),
                "init_mysql.sql must not create knowledge_docs");
        assertFalse(init.contains("CREATE TABLE IF NOT EXISTS chat_messages"),
                "init_mysql.sql must not create chat_messages");
        assertFalse(init.contains("CREATE TABLE IF NOT EXISTS chat_session_summaries"),
                "init_mysql.sql must not create chat_session_summaries");
        assertTrue(init.contains("sales_orders"), "business demo tables stay in init_mysql.sql");
        assertTrue(init.contains("huiyi_ro"), "readonly account stays in init_mysql.sql");
    }

    private String readAllMigrations() throws IOException {
        assertTrue(Files.isDirectory(MODULE), "db/migration directory missing");
        try (Stream<Path> paths = Files.list(MODULE)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }
    }
}
