package com.huiyi.assistant.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OutputGuardTest {

    @Test
    void preservesFrozenCopiesAndStripsStacksAndSecrets() {
        OutputGuard guard = new OutputGuard();
        assertEquals("工具调用失败，请稍后重试。", guard.sanitize("工具调用失败，请稍后重试。"));
        assertEquals("当前问题较复杂，请缩小范围后重试。", guard.sanitize("当前问题较复杂，请缩小范围后重试。"));
        assertFalse(guard.sanitize("boom\nException in thread \"main\" java.lang.RuntimeException")
                .contains("Exception in thread"));
        assertFalse(guard.sanitize("key sk-abcdefghijk").contains("sk-"));
        String springStack = "java.lang.NullPointerException\n"
                + "\tat org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1)\n"
                + "\tat java.base/java.lang.Thread.run(Thread.java:1)";
        String sanitizedStack = guard.sanitize(springStack);
        assertFalse(sanitizedStack.contains("DispatcherServlet"));
        assertFalse(sanitizedStack.contains("NullPointerException"));
        assertFalse(guard.sanitize("at com.mysql.cj.jdbc.ClientPreparedStatement.execute(ClientPreparedStatement.java:1)")
                .contains("com.mysql"));
    }
}
