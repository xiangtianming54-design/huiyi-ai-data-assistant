package com.huiyi.assistant.ops;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nginx limits /api/ and /api/v1/auth/ and does not listen on 443.
 */
class NginxLimitTest {

    private static final String API_ZONE =
            "limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;";
    private static final String AUTH_ZONE =
            "limit_req_zone $binary_remote_addr zone=auth:10m rate=1r/s;";
    private static final String LIMIT_STATUS = "limit_req_status 429;";
    private static final String API_BURST = "limit_req zone=api burst=20 nodelay;";
    private static final String AUTH_BURST = "limit_req zone=auth burst=5 nodelay;";

    private static final String[] PROXY_DIRECTIVES = {
            "proxy_pass http://backend:8080;",
            "proxy_http_version 1.1;",
            "proxy_set_header Host $host;",
            "proxy_set_header X-Real-IP $remote_addr;",
            "proxy_buffering off;",
            "proxy_cache off;",
            "proxy_read_timeout 300s;"
    };

    @Test
    void nginxConfRateLimitsApiAndAuthWithoutListeningOn443() throws IOException {
        String conf = read(Path.of("../frontend/nginx.conf"));

        int serverAt = conf.indexOf("server {");
        assertTrue(serverAt >= 0, "nginx.conf must contain one server block");
        String head = conf.substring(0, serverAt);
        assertTrue(head.contains(API_ZONE), "api zone belongs at the top of nginx.conf");
        assertTrue(head.contains(AUTH_ZONE), "auth zone belongs at the top of nginx.conf");
        assertTrue(head.contains(LIMIT_STATUS), "over-limit status belongs at the top of nginx.conf");

        assertEquals(1, countOccurrences(conf, "server {"), "nginx.conf must contain exactly one server block");
        assertFalse(conf.contains("location /actuator"), "nginx must not proxy /actuator");

        String auth = locationBlock(conf, "/api/v1/auth/");
        String api = locationBlock(conf, "/api/");
        assertTrue(auth.contains(AUTH_BURST), "auth location must limit at burst=5");
        assertTrue(api.contains(API_BURST), "api location must limit at burst=20");
        for (String directive : PROXY_DIRECTIVES) {
            assertTrue(auth.contains(directive), "auth location missing " + directive);
            assertTrue(api.contains(directive), "api location missing " + directive);
        }

        assertTrue(uncommentedLineEquals(conf, "gzip on;"), "gzip on must be an active directive");
        assertTrue(gzipTypesCoverRequiredMimeTypes(conf),
                "gzip_types must include text/css, application/javascript, application/json, image/svg+xml");

        assertListen443OnlyInTrailingComment(conf);
    }

    private static boolean gzipTypesCoverRequiredMimeTypes(String conf) {
        for (String line : conf.split("\\R", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (!trimmed.startsWith("gzip_types")) {
                continue;
            }
            return trimmed.contains("text/css")
                    && trimmed.contains("application/javascript")
                    && trimmed.contains("application/json")
                    && trimmed.contains("image/svg+xml");
        }
        return false;
    }

    private static boolean uncommentedLineEquals(String conf, String directive) {
        for (String line : conf.split("\\R", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (directive.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private static void assertListen443OnlyInTrailingComment(String conf) {
        String[] lines = conf.split("\\R", -1);
        int lastContent = -1;
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].isBlank()) {
                lastContent = i;
                break;
            }
        }
        assertTrue(lastContent >= 0, "nginx.conf is empty");

        StringBuilder trailing = new StringBuilder();
        for (int i = lastContent; i >= 0; i--) {
            String trimmed = lines[i].strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.startsWith("#")) {
                break;
            }
            trailing.insert(0, trimmed).insert(0, "\n");
        }
        assertTrue(trailing.toString().contains("listen 443 ssl"),
                "nginx.conf must end with a comment that mentions listen 443 ssl");
        assertTrue(trailing.toString().contains("ssl_certificate"),
                "the trailing comment must name a certificate path");

        int lineNo = 0;
        for (String line : lines) {
            lineNo++;
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            assertFalse(trimmed.contains("listen 443"),
                    "nginx.conf line " + lineNo + " must not listen on 443");
        }
    }

    /**
     * {@code location /api/} must not match {@code location /api/v1/auth/}.
     */
    private static String locationBlock(String conf, String path) {
        String needle = "location " + path;
        int from = 0;
        while (from < conf.length()) {
            int start = conf.indexOf(needle, from);
            assertTrue(start >= 0, "missing " + needle);
            int after = start + needle.length();
            if (after < conf.length()) {
                char next = conf.charAt(after);
                if (next != '{' && !Character.isWhitespace(next)) {
                    from = after;
                    continue;
                }
            }
            int brace = conf.indexOf('{', start);
            assertTrue(brace >= 0, "unclosed " + needle);
            int depth = 0;
            for (int i = brace; i < conf.length(); i++) {
                char c = conf.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return conf.substring(start, i + 1);
                    }
                }
            }
            break;
        }
        throw new AssertionError("unclosed " + needle);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (from < text.length()) {
            int i = text.indexOf(needle, from);
            if (i < 0) {
                return count;
            }
            count++;
            from = i + needle.length();
        }
        return count;
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "missing " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
