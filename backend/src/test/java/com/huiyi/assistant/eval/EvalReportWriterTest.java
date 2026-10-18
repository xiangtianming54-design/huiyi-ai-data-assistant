package com.huiyi.assistant.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalReportWriterTest {

    @Test
    void redactsSecrets() throws Exception {
        Path json = Files.createTempFile("eval", ".json");
        Path md = Files.createTempFile("eval", ".md");
        EvalReportWriter writer = new EvalReportWriter();
        writer.write(List.of(new EvalReportRow(
                "x", false, Map.of("answer", "key sk-abcdefghijk"), Map.of(), "answerEquals")), json, md);
        String body = Files.readString(json) + Files.readString(md);
        assertFalse(body.contains("sk-abcdefghijk"));
        assertTrue(body.contains("x"));
    }

    @Test
    void redactsWholeStackSegment() throws Exception {
        Path json = Files.createTempFile("eval", ".json");
        Path md = Files.createTempFile("eval", ".md");
        EvalReportWriter writer = new EvalReportWriter();
        writer.write(List.of(new EvalReportRow(
                "x",
                false,
                Map.of("answer", "ok\nException in thread \"main\"\nat com.huiyi.Foo.bar(Foo.java:1)"),
                Map.of(),
                "answerEquals")), json, md);
        String body = Files.readString(json) + Files.readString(md);
        assertFalse(body.contains("at com.huiyi"));
        assertFalse(body.contains("Exception in thread"));
        assertTrue(body.contains("x"));
    }
}
