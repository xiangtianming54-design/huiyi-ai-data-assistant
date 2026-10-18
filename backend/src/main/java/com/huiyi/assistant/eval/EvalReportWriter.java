package com.huiyi.assistant.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class EvalReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void write(List<EvalReportRow> rows, Path json, Path md) {
        try {
            Files.createDirectories(json.getParent() == null ? Path.of(".") : json.getParent());
            if (md.getParent() != null) {
                Files.createDirectories(md.getParent());
            }
            String jsonBody = redact(MAPPER.writeValueAsString(rows == null ? List.of() : rows));
            String mdBody = redact(toMarkdown(rows == null ? List.of() : rows));
            Files.writeString(json, jsonBody);
            Files.writeString(md, mdBody);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String toMarkdown(List<EvalReportRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("| id | passed | mismatch |\n");
        sb.append("|---|---|---|\n");
        for (EvalReportRow row : rows) {
            sb.append("| ")
                    .append(nullToEmpty(row.id()))
                    .append(" | ")
                    .append(row.passed())
                    .append(" | ")
                    .append(nullToEmpty(row.mismatch()))
                    .append(" |\n");
        }
        return sb.toString();
    }

    static String redact(String body) {
        if (body == null) {
            return "";
        }
        return body
                .replaceAll("sk-[A-Za-z0-9]{8,}", "[redacted]")
                .replaceAll("API_KEY=\\S*", "[redacted]")
                .replace("Exception in thread", "[redacted]")
                .replace("Traceback (most recent call last)", "[redacted]");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
