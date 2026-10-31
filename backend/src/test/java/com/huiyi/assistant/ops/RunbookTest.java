package com.huiyi.assistant.ops;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v1.4 运维说明必须写明部署、回滚、备份、恢复、告警和 certbot 附录，
 * 且路线图把当前开发指到本计划，而不是写成已发布。
 */
class RunbookTest {

    private static final String PLAN = "superpowers/plans/2026-10-31-v14-production-ready.md";
    private static final String SPEC = "superpowers/specs/2026-10-31-v14-production-ready.md";
    private static final String DEV_COMPOSE =
            "docker compose -f docker-compose.yml -f compose.dev.yml";

    @Test
    void runbookCoversDeployRollbackBackupRestoreAlertsAndCertbot() throws IOException {
        String runbook = read(Path.of("../docs/ops/runbook.md"));
        assertAll(
                () -> assertHeading(runbook, "部署"),
                () -> assertHeading(runbook, "回滚"),
                () -> assertHeading(runbook, "备份"),
                () -> assertHeading(runbook, "恢复"),
                () -> assertHeading(runbook, "告警响应"),
                () -> assertHeading(runbook, "常见故障"),
                () -> assertHeading(runbook, "certbot"),
                () -> assertTrue(section(runbook, "certbot").contains("本版不执行"),
                        "certbot appendix must say this version does not run it"),
                () -> assertTrue(section(runbook, "恢复").contains("ALTER USER"),
                        "recovery must change an existing huiyi_ro password with ALTER USER"),
                () -> assertTrue(section(runbook, "恢复").contains("huiyi_ro"),
                        "recovery must name huiyi_ro"),
                () -> assertTrue(section(runbook, "恢复").contains("初始化脚本不会重跑"),
                        "recovery must say the init script does not run again"),
                () -> assertTrue(section(runbook, "恢复").contains("--env-file .env.staging"),
                        "recovery must use the staging env file"),
                () -> assertTrue(section(runbook, "恢复").contains("--env-file .env.prod"),
                        "recovery must use the prod env file"),
                () -> assertFalse(section(runbook, "恢复").contains(".env.<env>"),
                        "recovery must not expand <env> into .env.dev"),
                () -> assertFalse(section(runbook, "恢复").contains(".env.dev"),
                        "recovery must not point dev at .env.dev"),
                () -> assertTrue(section(runbook, "恢复").contains(
                                "sh -c 'mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\""),
                        "the container shell must expand MYSQL_ROOT_PASSWORD"),
                () -> assertTrue(section(runbook, "告警响应").contains("webhook skipped"),
                        "alerts must log webhook skipped when the webhook is empty"),
                () -> assertTrue(section(runbook, "告警响应").contains("退出码非 0"),
                        "alerts must exit non-zero when the webhook is empty"),
                () -> assertTrue(section(runbook, "回滚").contains("deploy.sh <env> <上一 tag>"),
                        "rollback command is deploy.sh <env> <previous tag>"),
                () -> assertTrue(section(runbook, "回滚").contains("不必还原 schema"),
                        "rollback must not restore schema because there is no new migration")
        );
    }

    @Test
    void roadmapPointsCurrentWorkAtV14PlanAndSpec() throws IOException {
        String roadmap = read(Path.of("../docs/roadmap.md"));
        String current = section(roadmap, "当前开发");
        assertAll(
                () -> assertTrue(roadmap.contains("v1.4.0"), "roadmap must name v1.4.0"),
                () -> assertTrue(roadmap.contains(PLAN), "roadmap must link this plan"),
                () -> assertTrue(roadmap.contains(SPEC), "roadmap must link this spec"),
                () -> assertTrue(current.contains("v1.4.0"), "当前开发 must name v1.4.0"),
                () -> assertTrue(current.contains(PLAN), "当前开发 must link this plan"),
                () -> assertTrue(current.contains("尚未发布"), "v1.4 must stay unreleased"),
                () -> assertFalse(current.replace("尚未发布", "").contains("已发布"),
                        "当前开发 must not mark v1.4 released")
        );
    }

    @Test
    void versionNotesDescribeV14WithoutARelease() throws IOException {
        String development = read(Path.of("../docs/development.md"));
        String demo = read(Path.of("../docs/demo.md"));
        String readme = read(Path.of("../README.md"));
        String spec = read(Path.of("../docs/" + SPEC));
        assertAll(
                () -> assertTrue(development.contains(DEV_COMPOSE),
                        "development.md compose commands must use the dev override"),
                () -> assertTrue(demo.contains(DEV_COMPOSE),
                        "demo.md compose commands must use the dev override"),
                () -> {
                    String notes = section(development, "v1.4 变更");
                    assertTrue(notes.contains("deploy.sh <env> <上一 tag>"),
                            "v1.4 notes must name the rollback command");
                    assertTrue(notes.contains("没有新迁移"),
                            "v1.4 notes must say there is no new migration");
                    assertTrue(notes.contains("不必还原 schema"),
                            "v1.4 notes must say rollback does not restore schema");
                    assertTrue(notes.contains("尚未发布"),
                            "v1.4 notes must stay a draft");
                },
                () -> assertTrue(readme.contains("当前迭代"),
                        "README roadmap sentence must name the current iteration"),
                () -> assertTrue(readme.contains("docs/" + PLAN),
                        "README must link this plan"),
                () -> assertTrue(readme.contains("尚未发布"),
                        "README must not present v1.4 as released"),
                () -> assertTrue(spec.contains("../plans/2026-10-31-v14-production-ready.md"),
                        "spec header must keep the plan link"),
                () -> assertFalse(spec.contains("尚未开工"),
                        "spec header must point at the plan instead of 尚未开工"),
                () -> assertTrue(spec.contains("尚未发布"),
                        "spec must not mark v1.4 released")
        );
    }

    private static void assertHeading(String text, String title) {
        assertTrue(hasHeading(text, title), "missing heading " + title);
    }

    private static boolean hasHeading(String text, String title) {
        for (String line : text.split("\\R", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("#") && trimmed.contains(title)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Body under the first heading whose text contains {@code title},
     * stopping at the next heading of the same or higher level.
     */
    private static String section(String text, String title) {
        String[] lines = text.split("\\R", -1);
        int start = -1;
        int level = 0;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (!trimmed.startsWith("#")) {
                continue;
            }
            int marks = 0;
            while (marks < trimmed.length() && trimmed.charAt(marks) == '#') {
                marks++;
            }
            if (marks > 0 && trimmed.contains(title)) {
                start = i + 1;
                level = marks;
                break;
            }
        }
        assertTrue(start >= 0, "missing heading " + title);
        StringBuilder body = new StringBuilder();
        body.append(lines[start - 1]).append('\n');
        for (int i = start; i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (trimmed.startsWith("#")) {
                int marks = 0;
                while (marks < trimmed.length() && trimmed.charAt(marks) == '#') {
                    marks++;
                }
                if (marks > 0 && marks <= level) {
                    break;
                }
            }
            body.append(lines[i]).append('\n');
        }
        return body.toString();
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "missing " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
