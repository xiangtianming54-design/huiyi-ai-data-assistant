package com.huiyi.assistant.quota;

import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.auth.AuthService;
import com.huiyi.assistant.auth.JwtTokenService;
import com.huiyi.assistant.auth.LoginAttemptService;
import com.huiyi.assistant.auth.PasswordService;
import com.huiyi.assistant.config.AuthProperties;
import com.huiyi.assistant.mapper.RefreshTokenMapper;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.mapper.WorkspaceMapper;
import com.huiyi.assistant.mapper.WorkspaceMemberMapper;
import com.huiyi.assistant.model.dto.RegisterRequest;
import com.huiyi.assistant.model.entity.User;
import com.huiyi.assistant.model.entity.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTierAssignmentTest {

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
    void registerInsertsTierOneAndHasNoPromotionApi() {
        for (Method method : AuthService.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            String name = method.getName();
            assertFalse(name.contains("updateTier"), name);
            assertFalse(name.contains("changeTier"), name);
            assertFalse(name.contains("promote"), name);
        }

        when(userMapper.selectByUsername("alice")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            inserted.setId(10L);
            return 1;
        });
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        workspace.setCode("default");
        when(workspaceMapper.selectByCode("default")).thenReturn(workspace);
        when(workspaceMemberMapper.selectByWorkspaceAndUser(1L, 10L)).thenReturn(null);

        authService.register(new RegisterRequest("alice", "strong-password", "Alice"));

        ArgumentCaptor<User> inserted = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(inserted.capture());
        assertEquals(1, inserted.getValue().getUserTier());
    }
}
