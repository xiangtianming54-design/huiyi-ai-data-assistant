package com.huiyi.assistant.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageClientTest {

    @TempDir
    Path root;

    @Test
    void saveResolveAndDeleteStayUnderRoot() throws Exception {
        LocalFileStorageClient c = new LocalFileStorageClient(root);
        String key = c.save(9L, 55L, "hi".getBytes(StandardCharsets.UTF_8), "a.md");
        assertEquals("9/55/a.md", key);
        assertTrue(Files.exists(root.resolve("9").resolve("55").resolve("a.md")));
        assertTrue(c.exists(key));
        assertThrows(IllegalArgumentException.class, () -> c.resolve("../escape.txt"));
        c.delete(key);
        assertFalse(c.exists(key));
    }

    @Test
    void saveStripsPathFromOriginalFilename() {
        LocalFileStorageClient c = new LocalFileStorageClient(root);
        String key = c.save(9L, 55L, "x".getBytes(StandardCharsets.UTF_8), "../x.md");
        assertTrue(key.startsWith("9/55/"));
        assertFalse(key.contains(".."));
        Path resolved = c.resolve(key);
        assertTrue(resolved.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize()));
    }
}
