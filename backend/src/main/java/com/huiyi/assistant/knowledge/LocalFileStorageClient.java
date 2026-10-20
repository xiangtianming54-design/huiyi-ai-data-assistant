package com.huiyi.assistant.knowledge;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class LocalFileStorageClient {

    private final Path root;

    @Autowired
    public LocalFileStorageClient(@Value("${huiyi.knowledge.storage-dir:./data/knowledge}") String rootDir) {
        this(Path.of(rootDir));
    }

    public LocalFileStorageClient(Path rootDir) {
        this.root = rootDir.toAbsolutePath().normalize();
    }

    public String save(long workspaceId, long docId, byte[] content, String originalFilename) {
        String filename = sanitizeFilename(originalFilename);
        String key = workspaceId + "/" + docId + "/" + filename;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content == null ? new byte[0] : content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return key;
    }

    public Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey required");
        }
        Path resolved = root.resolve(storageKey).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("storage key escapes root");
        }
        return resolved;
    }

    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        Path path = resolve(storageKey);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean exists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        return Files.exists(resolve(storageKey));
    }

    private static String sanitizeFilename(String originalFilename) {
        String name = originalFilename;
        if (name == null || name.isBlank()) {
            return "file";
        }
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
            return "file";
        }
        return name;
    }
}
