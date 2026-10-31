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
