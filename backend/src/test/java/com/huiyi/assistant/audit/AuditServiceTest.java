package com.huiyi.assistant.audit;

import com.huiyi.assistant.mapper.AuditEventMapper;
import com.huiyi.assistant.model.entity.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditEventMapper auditEventMapper;

    AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditEventMapper);
    }

    @Test
    void loginFailureDoesNotRecordPassword() {
        auditService.record(AuditAction.AUTH_LOGIN_FAILED, "FAILED", null, "alice", null,
                "user", "alice", Map.of("password", "secret-password", "reason", "mismatch"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventMapper).insert(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(AuditAction.AUTH_LOGIN_FAILED, event.getAction());
        assertEquals("FAILED", event.getResultStatus());
        assertEquals("alice", event.getUsername());
        assertFalse(event.getDetailJson().contains("secret-password"));
        assertFalse(event.getDetailJson().contains("\"password\""));
        assertEquals(true, event.getDetailJson().contains("mismatch"));
    }

    @Test
    void accessDeniedIsRecorded() {
        auditService.accessDenied(10L, "alice", 2L, "不是该工作区的有效成员");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventMapper).insert(captor.capture());
        AuditEvent event = captor.getValue();
        assertEquals(AuditAction.ACCESS_DENIED, event.getAction());
        assertEquals("DENIED", event.getResultStatus());
        assertEquals(10L, event.getUserId());
        assertEquals(2L, event.getWorkspaceId());
    }

    @Test
    void insertFailureDoesNotThrow() {
        doThrow(new RuntimeException("db down")).when(auditEventMapper).insert(any(AuditEvent.class));
        assertDoesNotThrow(() -> auditService.record(AuditAction.AUTH_LOGOUT, "SUCCESS",
                1L, "alice", null, "token", null, Map.of()));
    }
}
