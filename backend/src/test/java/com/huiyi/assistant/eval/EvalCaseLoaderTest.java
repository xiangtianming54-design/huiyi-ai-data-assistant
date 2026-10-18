package com.huiyi.assistant.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvalCaseLoaderTest {

    @TempDir
    Path dir;

    @Test
    void rejectsTenantKeysAndLoadsOk() throws Exception {
        Files.writeString(dir.resolve("bad.json"), "{\"id\":\"bad\",\"workspaceId\":1,\"question\":\"q\"}");
        EvalCaseLoader loader = new EvalCaseLoader();
        assertThrows(IllegalArgumentException.class, () -> loader.load(dir));
        Files.writeString(dir.resolve("ok.json"),
                "{\"id\":\"ok\",\"entrypoint\":\"chat_stream\",\"question\":\"q\",\"workspace\":\"default\"}");
        Files.delete(dir.resolve("bad.json"));
        List<EvalCase> cases = loader.load(dir);
        assertEquals("ok", cases.get(0).id());
    }
}
