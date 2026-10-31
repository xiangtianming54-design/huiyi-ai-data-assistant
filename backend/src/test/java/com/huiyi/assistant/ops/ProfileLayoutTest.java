package com.huiyi.assistant.ops;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * staging 与 prod 的 Spring profile 只覆盖日志、Actuator 暴露和 info，不复制数据源。
 */
class ProfileLayoutTest {

    private static final String[] REQUIRED = {
            "logging.file.name: /var/log/huiyi/app.log",
            "include: health,info,metrics,prometheus",
            "management.info.env.enabled: true",
            "info.git.commit: ${GIT_COMMIT:unknown}",
            "info.app.version: ${APP_VERSION:unknown}"
    };

    @Test
    void stagingProfileLogsAndExposesMetricsWithoutDatasource() throws IOException {
        assertProfile("application-staging.yml");
    }

    @Test
    void prodProfileLogsAndExposesMetricsWithoutDatasource() throws IOException {
        assertProfile("application-prod.yml");
    }

    private static void assertProfile(String fileName) throws IOException {
        String yaml = read(Path.of("src/main/resources", fileName));
        for (String required : REQUIRED) {
            assertTrue(yaml.contains(required), fileName + " must contain " + required);
        }
        assertFalse(yaml.contains("jdbc:"), fileName + " must not copy a datasource");
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "missing " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
