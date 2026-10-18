package com.huiyi.assistant.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class EvalReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final Pattern SK = Pattern.compile("sk-[A-Za-z0-9]{8,}");

    public void write(List<EvalReportRow> rows, Path json, Path md) {
        try {
            Files.createDirectories(json.getParent() == null ? Path.of(".") : json.getParent());
            if (md.getParent() != null) {
                Files.createDirectories(md.getParent());
            }
            JsonNode tree = MAPPER.valueToTree(rows == null ? List.of() : rows);
            redactNode(tree);
            Files.writeString(json, MAPPER.writeValueAsString(tree));
            Files.writeString(md, toMarkdown(rows == null ? List.of() : rows));
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
                    .append(redactField(nullToEmpty(row.id())))
                    .append(" | ")
                    .append(row.passed())
                    .append(" | ")
                    .append(redactField(nullToEmpty(row.mismatch())))
                    .append(" |\n");
        }
        return sb.toString();
    }

    static void redactNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                if (value != null && value.isTextual() && tainted(value.asText())) {
                    obj.set(entry.getKey(), TextNode.valueOf("[redacted]"));
                } else {
                    redactNode(value);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode value = arr.get(i);
                if (value != null && value.isTextual() && tainted(value.asText())) {
                    arr.set(i, TextNode.valueOf("[redacted]"));
                } else {
                    redactNode(value);
                }
            }
        }
    }

    static String redactField(String body) {
        return tainted(body) ? "[redacted]" : (body == null ? "" : body);
    }

    static boolean tainted(String body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        return SK.matcher(body).find()
                || body.contains("API_KEY=")
                || body.contains("Exception in thread")
                || body.contains("Traceback (most recent call last)");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
