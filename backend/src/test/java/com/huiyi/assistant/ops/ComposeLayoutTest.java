package com.huiyi.assistant.ops;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公共 Compose 不发布宿主机端口；dev override 补回现网端口，且两边都不设项目名。
 */
class ComposeLayoutTest {

    @Test
    void sharedComposeKeepsKnowledgeVolumeAndPublishesNoHostPorts() throws IOException {
        String compose = read(Path.of("../docker-compose.yml"));
        assertTrue(compose.contains("knowledge-data:/var/huiyi/knowledge"),
                "backend must mount knowledge-data at /var/huiyi/knowledge");
        assertTrue(compose.contains("HUIYI_KNOWLEDGE_STORAGE_DIR"),
                "backend must set HUIYI_KNOWLEDGE_STORAGE_DIR");
        assertFalse(compose.contains("8080:8080"), "shared compose must not publish backend 8080");
        assertFalse(compose.contains("5173:80"), "shared compose must not publish frontend 5173");
        assertFalse(compose.contains("6379:6379"), "shared compose must not publish redis 6379");
        assertNoComposeProjectName("docker-compose.yml", compose);
    }

    @Test
    void devOverridePublishesExistingHostPortsOnly() throws IOException {
        String dev = read(Path.of("../compose.dev.yml"));
        assertTrue(dev.contains("${MYSQL_HOST_PORT:-3307}:3306"), "dev mysql host port");
        assertTrue(dev.contains("6379:6379"), "dev redis host port");
        assertTrue(dev.contains("127.0.0.1:8001:8001"), "dev python host port");
        assertTrue(dev.contains("8080:8080"), "dev backend host port");
        assertTrue(dev.contains("5173:80"), "dev frontend host port");
        assertFalse(dev.contains(":?必须配置"), "dev override must not require staging secrets");
        assertNoComposeProjectName("compose.dev.yml", dev);
    }

    @Test
    void runLocalStartsInfraWithDevOverride() throws IOException {
        String script = read(Path.of("../scripts/run_local.sh"));
        assertTrue(script.contains("-f docker-compose.yml -f compose.dev.yml up -d mysql redis"),
                "run_local.sh must start mysql and redis with the dev override");
    }

    @Test
    void sharedComposeMountsReadOnlyPasswordInitScript() throws IOException {
        String compose = read(Path.of("../docker-compose.yml"));
        assertTrue(compose.contains("./scripts/init_ro_password.sh:/docker-entrypoint-initdb.d/03_ro_password.sh:ro"),
                "mysql must mount the read-only password init script");
        assertTrue(compose.contains("./scripts/mysql_entrypoint.sh:/huiyi-mysql-entrypoint.sh:ro"),
                "mysql must mount the startup entrypoint that re-applies the read-only password");
        assertTrue(compose.contains("bash\", \"/huiyi-mysql-entrypoint.sh"),
                "mysql entrypoint must run the startup script");
        assertNoComposeProjectName("docker-compose.yml", compose);
    }

    @Test
    void stagingOverrideRequiresSecretsAndSlowQueryLog() throws IOException {
        String staging = read(Path.of("../compose.staging.yml"));
        assertComposeProjectName("compose.staging.yml", staging, "huiyi-staging");
        assertStagingOrProdSecrets("compose.staging.yml", staging);
        assertTrue(staging.contains("SPRING_PROFILES_ACTIVE: docker,staging"), "staging spring profile");
        assertTrue(staging.contains("slow_query_log=1"), "staging mysql must enable slow_query_log=1");
        assertTrue(staging.contains("--long_query_time=1"), "staging long_query_time");
        assertTrue(staging.contains("--slow_query_log_file=/var/lib/mysql/slow.log"), "staging slow log file");
        assertTrue(staging.contains("GIT_COMMIT: ${GIT_COMMIT:-unknown}"), "staging must pass GIT_COMMIT into backend");
        assertTrue(staging.contains("APP_VERSION: ${APP_VERSION:-unknown}"), "staging must pass APP_VERSION into backend");
    }

    @Test
    void prodOverrideRequiresSecretsWithoutSlowQueryLog() throws IOException {
        String prod = read(Path.of("../compose.prod.yml"));
        assertComposeProjectName("compose.prod.yml", prod, "huiyi-prod");
        assertStagingOrProdSecrets("compose.prod.yml", prod);
        assertTrue(prod.contains("SPRING_PROFILES_ACTIVE: docker,prod"), "prod spring profile");
        assertFalse(prod.contains("docker,staging"), "prod must not use the staging profile");
        assertFalse(prod.contains("slow_query_log"), "prod must not set slow_query_log");
        assertTrue(prod.contains("GIT_COMMIT: ${GIT_COMMIT:-unknown}"), "prod must pass GIT_COMMIT into backend");
        assertTrue(prod.contains("APP_VERSION: ${APP_VERSION:-unknown}"), "prod must pass APP_VERSION into backend");
    }

    @Test
    void gitignoreCoversStagingEnvAndBackups() throws IOException {
        String ignore = read(Path.of("../.gitignore"));
        assertTrue(ignore.contains(".env.dev"), ".gitignore must ignore .env.dev");
        assertTrue(ignore.contains(".env.staging"), ".gitignore must ignore .env.staging");
        assertTrue(ignore.contains(".env.prod"), ".gitignore must ignore .env.prod");
        assertTrue(ignore.contains("data/backups/"), ".gitignore must ignore data/backups/");
    }

    @Test
    void envExampleKeepsDemoPasswordAndAddsEmptyPlaceholders() throws IOException {
        String example = read(Path.of("../.env.example"));
        assertTrue(example.contains("MYSQL_ROOT_PASSWORD=huiyi123"), "demo root password stays in .env.example");
        assertEmptyAssignment(example, "RO_DB_PASSWORD");
        assertEmptyAssignment(example, "REDIS_PASSWORD");
        assertEmptyAssignment(example, "DINGTALK_WEBHOOK_URL");
        assertEmptyAssignment(example, "BACKUP_DIR");
        assertEmptyAssignment(example, "BACKUP_RETAIN_DAYS");
        assertEmptyAssignment(example, "GIT_COMMIT");
        assertEmptyAssignment(example, "APP_VERSION");
        assertEmptyAssignment(example, "ALERT_DEDUP_MINUTES");
        assertEmptyAssignment(example, "SPRING_PROFILES_ACTIVE");
    }

    private static void assertEmptyAssignment(String text, String key) {
        boolean found = false;
        for (String line : text.split("\\R", -1)) {
            if (line.strip().equals(key + "=")) {
                found = true;
            }
        }
        assertTrue(found, "expected empty placeholder " + key + "=");
    }

    private static void assertStagingOrProdSecrets(String label, String text) {
        assertTrue(text.contains("80:80"), label + " must publish frontend 80:80");
        assertTrue(text.contains("${RO_DB_PASSWORD:?必须配置}"), label + " must require RO_DB_PASSWORD");
        assertTrue(text.contains("${MYSQL_ROOT_PASSWORD:?必须配置}"), label + " must require MYSQL_ROOT_PASSWORD");
        assertTrue(text.contains("${JWT_SECRET:?必须配置}"), label + " must require JWT_SECRET");
        assertTrue(text.contains("${REDIS_PASSWORD:?必须配置}"), label + " must require REDIS_PASSWORD");
        assertTrue(text.contains("--requirepass"), label + " redis must set --requirepass");
        assertTrue(text.contains("redis-cli"), label + " redis healthcheck must use redis-cli");
        assertTrue(text.contains("backend-logs:/var/log/huiyi"), label + " must mount backend logs");
        assertFalse(text.contains("8080:8080"), label + " must not publish backend 8080");
    }

    /**
     * Same rule as {@link #assertNoComposeProjectName}: a project name is a line
     * whose first token is {@code name:}. {@code container_name:} does not count.
     */
    private static void assertComposeProjectName(String label, String text, String expectedName) {
        boolean found = false;
        int lineNo = 0;
        for (String line : text.split("\\R", -1)) {
            lineNo++;
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+", 2);
            if (!"name:".equals(parts[0])) {
                continue;
            }
            String value = parts.length > 1 ? parts[1].strip() : "";
            assertTrue(expectedName.equals(value),
                    label + " line " + lineNo + " project name is " + value);
            found = true;
        }
        assertTrue(found, label + " must set compose project name " + expectedName);
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "missing " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * A compose project name is a line whose first token is {@code name:}.
     * {@code container_name:} is a different key and must stay allowed.
     */
    private static void assertNoComposeProjectName(String label, String text) {
        int lineNo = 0;
        for (String line : text.split("\\R", -1)) {
            lineNo++;
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String firstToken = trimmed.split("\\s+", 2)[0];
            assertFalse("name:".equals(firstToken),
                    label + " line " + lineNo + " sets a compose project name");
        }
    }
}
