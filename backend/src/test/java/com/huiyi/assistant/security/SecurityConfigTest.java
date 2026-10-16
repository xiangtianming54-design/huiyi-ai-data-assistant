package com.huiyi.assistant.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE complete() 会触发 ASYNC/ERROR dispatcher。STATELESS 下 JWT 过滤器不在异步派发上重跑，
 * 必须放行这两种 dispatcher，否则引用已到达但流无法正常结束。
 */
class SecurityConfigTest {

    @Test
    void permitsAsyncAndErrorDispatchersForSseCompletion() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/huiyi/assistant/security/SecurityConfig.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("dispatcherTypeMatchers(DispatcherType.ASYNC"),
                "ASYNC dispatcher must be permitAll so SseEmitter.complete() is not Access Denied");
        assertTrue(source.contains("DispatcherType.ERROR"),
                "ERROR dispatcher must be permitAll so committed SSE responses do not cascade");
        assertTrue(source.contains("/actuator/prometheus"),
                "Prometheus scrape endpoint must be permitAll");
        assertTrue(source.contains("/actuator/health"),
                "Actuator health must be permitAll");
    }
}
