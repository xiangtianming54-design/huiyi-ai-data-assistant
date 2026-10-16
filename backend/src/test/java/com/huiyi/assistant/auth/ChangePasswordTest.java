package com.huiyi.assistant.auth;

import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.config.AuthProperties;
import com.huiyi.assistant.mapper.RefreshTokenMapper;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.mapper.WorkspaceMapper;
import com.huiyi.assistant.mapper.WorkspaceMemberMapper;
import com.huiyi.assistant.model.dto.ChangePasswordRequest;
import com.huiyi.assistant.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePasswordTest {

    @Mock UserMapper userMapper;
    @Mock WorkspaceMapper workspaceMapper;
    @Mock WorkspaceMemberMapper workspaceMemberMapper;
    @Mock RefreshTokenMapper refreshTokenMapper;
    @Mock LoginAttemptService loginAttemptService;
    @Mock AuditService auditService;

    AuthProperties properties;
    PasswordService passwordService;
    JwtTokenService jwtTokenService;
    AuthService authService;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.setJwtSecret("test-secret-must-be-at-least-32-chars!");
        properties.setJwtIssuer("huiyi");
        properties.setJwtAudience("huiyi-api");
        properties.setAccessTokenTtlSeconds(1800);
        properties.setRefreshTokenTtlSeconds(604800);
        passwordService = new PasswordService();
        jwtTokenService = new JwtTokenService(properties);
        authService = new AuthService(properties, passwordService, jwtTokenService, loginAttemptService,
                userMapper, workspaceMapper, workspaceMemberMapper, refreshTokenMapper, auditService);
    }

    @Test
    void changePasswordRevokesRefreshAndBumpsTokenVersion() {
        User user = new User();
        user.setId(10L);
        user.setUsername("alice");
        user.setStatus("ACTIVE");
        user.setTokenVersion(3);
        user.setPasswordHash(passwordService.hash("old-password-x"));
        when(userMapper.selectById(10L)).thenReturn(user);

        authService.changePassword(10L, new ChangePasswordRequest("old-password-x", "new-password-x"));

        assertEquals(4, user.getTokenVersion());
        assertTrue(passwordService.matches("new-password-x", user.getPasswordHash()));
        verify(userMapper).updateById(user);
        verify(refreshTokenMapper).revokeAllActive(10L);
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        User user = new User();
        user.setId(10L);
        user.setStatus("ACTIVE");
        user.setPasswordHash(passwordService.hash("old-password-x"));
        when(userMapper.selectById(10L)).thenReturn(user);
        BizException ex = assertThrows(BizException.class,
                () -> authService.changePassword(10L, new ChangePasswordRequest("wrong-old-xx", "new-password-x")));
        assertEquals(400, ex.getCode());
    }
}
