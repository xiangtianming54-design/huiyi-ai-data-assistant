package com.huiyi.assistant.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.Map;

public final class ToolSchemaValidator {

    public static final String SCHEMA_INVALID = "schema_invalid";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public boolean isValid(String inputSchema, Map<String, Object> arguments) {
        try {
            JsonNode schema = MAPPER.readTree(inputSchema);
            JsonNode args = MAPPER.valueToTree(arguments == null ? Map.of() : arguments);
            return validObject(schema, args);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validObject(JsonNode schema, JsonNode args) {
        if (schema == null || !schema.isObject()) {
            return false;
        }
        if (!args.isObject()) {
            return false;
        }
        String type = text(schema.get("type"));
        if (type != null && !"object".equals(type)) {
            return false;
        }
        JsonNode properties = schema.path("properties");
        boolean additionalOk = !schema.path("additionalProperties").isBoolean()
                || schema.get("additionalProperties").asBoolean();
        if (!additionalOk) {
            Iterator<String> names = args.fieldNames();
            while (names.hasNext()) {
                if (!properties.has(names.next())) {
                    return false;
                }
            }
        }
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode name : required) {
                if (!args.has(name.asText())) {
                    return false;
                }
            }
        }
        if (!properties.isObject()) {
            return true;
        }
        Iterator<String> fields = args.fieldNames();
        while (fields.hasNext()) {
            String name = fields.next();
            if (properties.has(name) && !validValue(properties.get(name), args.get(name))) {
                return false;
            }
        }
        return true;
    }

    private static boolean validValue(JsonNode schema, JsonNode value) {
        String type = text(schema.get("type"));
        if ("string".equals(type)) {
            if (value == null || !value.isTextual()) {
                return false;
            }
            JsonNode min = schema.get("minLength");
            return min == null || !min.isNumber() || value.asText().length() >= min.asInt();
        }
        if ("array".equals(type)) {
            return value != null && value.isArray();
        }
        if ("object".equals(type)) {
            return value != null && value.isObject();
        }
        return true;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() || !node.isTextual() ? null : node.asText();
    }
}
