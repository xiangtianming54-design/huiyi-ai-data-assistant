package com.huiyi.assistant.eval;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class EvalCaseLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<EvalCase> load(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("eval dir missing: " + dir);
        }
        List<EvalCase> cases = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                String raw = Files.readString(file);
                if (raw.contains("\"workspaceId\"") || raw.contains("\"userId\"")) {
                    throw new IllegalArgumentException("tenant keys forbidden: " + file);
                }
                cases.add(MAPPER.readValue(raw, EvalCase.class));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cases.sort(Comparator.comparing(EvalCase::id, Comparator.nullsLast(String::compareTo)));
        return cases;
    }

    public static Path resolveEvalDir() {
        Path relative = Path.of("..", "testdata", "eval");
        if (Files.isDirectory(relative)) {
            return relative;
        }
        return Path.of("testdata", "eval");
    }
}
