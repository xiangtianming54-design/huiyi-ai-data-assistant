package com.huiyi.assistant.auth;

import com.huiyi.assistant.audit.AuditAction;
import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.config.AuthProperties;
import com.huiyi.assistant.mapper.RefreshTokenMapper;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.mapper.WorkspaceMapper;
import com.huiyi.assistant.mapper.WorkspaceMemberMapper;
import com.huiyi.assistant.model.dto.LoginRequest;
import com.huiyi.assistant.model.dto.RegisterRequest;
import com.huiyi.assistant.model.entity.RefreshToken;
import com.huiyi.assistant.model.entity.User;
import com.huiyi.assistant.model.entity.Workspace;
import com.huiyi.assistant.model.entity.WorkspaceMember;
import com.huiyi.assistant.model.vo.AuthTokensVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    static final String LOGIN_ERROR = "用户名或密码错误，或账号暂时不可用。";

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
        properties.setRegisterEnabled(true);
        properties.setDefaultMemberRole("VIEWER");
        properties.setDefaultWorkspaceCode("default");
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
    void registerCreatesViewerOnDefaultWorkspace() {
        when(userMapper.selectByUsername("alice")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return 1;
        });
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        workspace.setCode("default");
        when(workspaceMapper.selectByCode("default")).thenReturn(workspace);
        when(workspaceMemberMapper.selectByWorkspaceAndUser(1L, 10L)).thenReturn(null);

        AuthTokensVO tokens = authService.register(new RegisterRequest("alice", "strong-password", "Alice"));

        assertEquals(10L, tokens.getUserId());
        assertEquals("alice", tokens.getUsername());
        assertEquals(1L, tokens.getDefaultWorkspaceId());
        assertNotNull(tokens.getAccessToken());
        assertNotNull(tokens.getRefreshToken());
        ArgumentCaptor<WorkspaceMember> member = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(workspaceMemberMapper).insert(member.capture());
        assertEquals("VIEWER", member.getValue().getRoleCode());
        assertEquals("ACTIVE", member.getValue().getStatus());
    }

    @Test
    void duplicateRegisterFails() {
        User existing = new User();
        existing.setUsername("alice");
        when(userMapper.selectByUsername("alice")).thenReturn(existing);
        BizException ex = assertThrows(BizException.class,
                () -> authService.register(new RegisterRequest("alice", "strong-password", "Alice")));
        assertEquals(400, ex.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void registerDisabledFails() {
        properties.setRegisterEnabled(false);
        BizException ex = assertThrows(BizException.class,
                () -> authService.register(new RegisterRequest("alice", "strong-password", "Alice")));
        assertEquals(400, ex.getCode());
    }

    @Test
    void loginSuccessIssuesTokens() {
        User user = activeUser();
        when(loginAttemptService.isLocked("alice")).thenReturn(false);
        when(userMapper.selectByUsername("alice")).thenReturn(user);
        AuthTokensVO tokens = authService.login(new LoginRequest("alice", "strong-password"));
        assertEquals(10L, tokens.getUserId());
        verify(loginAttemptService).clear("alice");
        verify(refreshTokenMapper).insert(any(RefreshToken.class));
    }

    @Test
    void loginFailureIsAuditedWithoutPassword() {
        when(loginAttemptService.isLocked("alice")).thenReturn(false);
        when(userMapper.selectByUsername("alice")).thenReturn(activeUser());
        assertThrows(BizException.class, () -> authService.login(new LoginRequest("alice", "wrong-password-x")));
        verify(auditService).record(org.mockito.ArgumentMatchers.eq(AuditAction.AUTH_LOGIN_FAILED),
                org.mockito.ArgumentMatchers.eq("FAILED"),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("alice"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("user"),
                org.mockito.ArgumentMatchers.eq("alice"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownUserSameError() {
        when(loginAttemptService.isLocked("ghost")).thenReturn(false);
        when(userMapper.selectByUsername("ghost")).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> authService.login(new LoginRequest("ghost", "strong-password")));
        assertEquals(LOGIN_ERROR, ex.getMessage());
        verify(loginAttemptService).recordFailure("ghost");
    }

    @Test
    void badPasswordSameError() {
        when(loginAttemptService.isLocked("alice")).thenReturn(false);
        when(userMapper.selectByUsername("alice")).thenReturn(activeUser());
        BizException ex = assertThrows(BizException.class,
                () -> authService.login(new LoginRequest("alice", "wrong-password-x")));
        assertEquals(LOGIN_ERROR, ex.getMessage());
        verify(loginAttemptService).recordFailure("alice");
    }

    @Test
    void disabledAccountSameError() {
        User user = activeUser();
        user.setStatus("DISABLED");
        when(loginAttemptService.isLocked("alice")).thenReturn(false);
        when(userMapper.selectByUsername("alice")).thenReturn(user);
        BizException ex = assertThrows(BizException.class,
                () -> authService.login(new LoginRequest("alice", "strong-password")));
        assertEquals(LOGIN_ERROR, ex.getMessage());
    }

    @Test
    void refreshRevokedTokenFails() {
        when(refreshTokenMapper.selectActiveByHash(anyString())).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> authService.refresh("dead-token-value"));
        assertEquals(401, ex.getCode());
    }

    @Test
    void logoutRevokesRefreshToken() {
        authService.logout("refresh-token-value");
        verify(refreshTokenMapper).revokeByHash(anyString());
    }

    @Test
    void lockAfterFailures() {
        when(loginAttemptService.isLocked("alice")).thenReturn(true);
        BizException ex = assertThrows(BizException.class,
                () -> authService.login(new LoginRequest("alice", "strong-password")));
        assertEquals(LOGIN_ERROR, ex.getMessage());
        verify(userMapper, never()).selectByUsername(anyString());
    }

    @Test
    void tokenVersionMismatchIsRejected() {
        User user = activeUser();
        user.setTokenVersion(3);
        String access = jwtTokenService.createAccessToken(user);
        user.setTokenVersion(4);
        assertTrue(jwtTokenService.isTokenVersionMismatch(access, user));
    }

    private User activeUser() {
        User user = new User();
        user.setId(10L);
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setStatus("ACTIVE");
        user.setTokenVersion(0);
        user.setPasswordHash(passwordService.hash("strong-password"));
        return user;
    }
}
