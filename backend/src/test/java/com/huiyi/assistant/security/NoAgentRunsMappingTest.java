package com.huiyi.assistant.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NoAgentRunsMappingTest {

    @Test
    void noAgentRunsController() throws Exception {
        String joined;
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            joined = paths.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .collect(Collectors.joining("\n"));
        }
        assertFalse(joined.contains("/agent/runs"));
        assertFalse(joined.contains("ReActPlanner"));
    }
}
