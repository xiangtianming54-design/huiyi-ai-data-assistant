package com.huiyi.assistant.auth;

import com.huiyi.assistant.audit.AuditAction;
import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.config.AuthProperties;
import com.huiyi.assistant.mapper.RefreshTokenMapper;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.mapper.WorkspaceMapper;
import com.huiyi.assistant.mapper.WorkspaceMemberMapper;
import com.huiyi.assistant.model.dto.ChangePasswordRequest;
import com.huiyi.assistant.model.dto.LoginRequest;
import com.huiyi.assistant.model.dto.RegisterRequest;
import com.huiyi.assistant.model.entity.RefreshToken;
import com.huiyi.assistant.model.entity.User;
import com.huiyi.assistant.model.entity.Workspace;
import com.huiyi.assistant.model.entity.WorkspaceMember;
import com.huiyi.assistant.model.vo.AuthTokensVO;
import com.huiyi.assistant.model.vo.MeVO;
import com.huiyi.assistant.model.vo.WorkspaceMembershipVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    public static final String LOGIN_ERROR = "用户名或密码错误，或账号暂时不可用。";
    public static final String STATUS_ACTIVE = "ACTIVE";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthProperties properties;
    private final PasswordService passwordService;
    private final JwtTokenService jwtTokenService;
    private final LoginAttemptService loginAttemptService;
    private final UserMapper userMapper;
    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final AuditService auditService;

    public AuthService(AuthProperties properties, PasswordService passwordService, JwtTokenService jwtTokenService,
                       LoginAttemptService loginAttemptService, UserMapper userMapper, WorkspaceMapper workspaceMapper,
                       WorkspaceMemberMapper workspaceMemberMapper, RefreshTokenMapper refreshTokenMapper,
                       AuditService auditService) {
        this.properties = properties;
        this.passwordService = passwordService;
        this.jwtTokenService = jwtTokenService;
        this.loginAttemptService = loginAttemptService;
        this.userMapper = userMapper;
        this.workspaceMapper = workspaceMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.auditService = auditService;
    }

    @Transactional
    public AuthTokensVO register(RegisterRequest request) {
        if (!properties.isRegisterEnabled()) {
            throw new BizException(400, "当前未开放注册");
        }
        passwordService.validate(request.getPassword());
        if (!StringUtils.hasText(request.getUsername()) || request.getUsername().length() < 3) {
            throw new BizException(400, "用户名至少 3 位");
        }
        if (userMapper.selectByUsername(request.getUsername()) != null) {
            throw new BizException(400, "用户名已被占用");
        }
        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setDisplayName(StringUtils.hasText(request.getDisplayName()) ? request.getDisplayName().trim() : request.getUsername());
        user.setPasswordHash(passwordService.hash(request.getPassword()));
        user.setStatus(STATUS_ACTIVE);
        user.setTokenVersion(0);
        userMapper.insert(user);

        Workspace defaultWorkspace = workspaceMapper.selectByCode(properties.getDefaultWorkspaceCode());
        if (defaultWorkspace == null) {
            throw new BizException(500, "默认工作区不存在");
        }
        WorkspaceMember existing = workspaceMemberMapper.selectByWorkspaceAndUser(defaultWorkspace.getId(), user.getId());
        if (existing == null) {
            WorkspaceMember member = new WorkspaceMember();
            member.setWorkspaceId(defaultWorkspace.getId());
            member.setUserId(user.getId());
            member.setRoleCode(properties.getDefaultMemberRole());
            member.setStatus(STATUS_ACTIVE);
            member.setJoinedAt(LocalDateTime.now());
            workspaceMemberMapper.insert(member);
        }
        AuthTokensVO tokens = issueTokens(user, defaultWorkspace.getId());
        auditService.record(AuditAction.AUTH_REGISTER, "SUCCESS", user.getId(), user.getUsername(),
                defaultWorkspace.getId(), "user", String.valueOf(user.getId()), Map.of());
        return tokens;
    }

    public AuthTokensVO login(LoginRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        if (loginAttemptService.isLocked(username)) {
            auditService.record(AuditAction.RATE_LIMITED, "DENIED", null, username, null,
                    "user", username, Map.of("reason", "locked"));
            throw new BizException(401, LOGIN_ERROR);
        }
        User user = userMapper.selectByUsername(username);
        if (user == null || !STATUS_ACTIVE.equals(user.getStatus())
                || !passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(username);
            auditService.record(AuditAction.AUTH_LOGIN_FAILED, "FAILED", user == null ? null : user.getId(),
                    username, null, "user", username, Map.of("reason", "mismatch"));
            if (loginAttemptService.isLocked(username)) {
                auditService.record(AuditAction.RATE_LIMITED, "DENIED", null, username, null,
                        "user", username, Map.of("reason", "locked"));
            }
            throw new BizException(401, LOGIN_ERROR);
        }
        loginAttemptService.clear(username);
        Workspace defaultWorkspace = workspaceMapper.selectByCode(properties.getDefaultWorkspaceCode());
        Long defaultId = defaultWorkspace == null ? null : defaultWorkspace.getId();
        AuthTokensVO tokens = issueTokens(user, defaultId);
        auditService.record(AuditAction.AUTH_LOGIN_SUCCESS, "SUCCESS", user.getId(), user.getUsername(),
                defaultId, "user", String.valueOf(user.getId()), Map.of());
        return tokens;
    }

    @Transactional
    public AuthTokensVO refresh(String refreshToken) {
        String hash = hashToken(refreshToken);
        RefreshToken stored = refreshTokenMapper.selectActiveByHash(hash);
        if (stored == null) {
            throw new BizException(401, "登录已失效，请重新登录");
        }
        User user = userMapper.selectById(stored.getUserId());
        if (user == null || !STATUS_ACTIVE.equals(user.getStatus())) {
            refreshTokenMapper.revokeByHash(hash);
            throw new BizException(401, LOGIN_ERROR);
        }
        String nextRaw = randomToken();
        String nextHash = hashToken(nextRaw);
        refreshTokenMapper.updateById(patchRevoked(stored, nextHash));
        persistRefresh(user.getId(), nextHash);
        Workspace defaultWorkspace = workspaceMapper.selectByCode(properties.getDefaultWorkspaceCode());
        AuthTokensVO tokens = AuthTokensVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .accessToken(jwtTokenService.createAccessToken(user))
                .refreshToken(nextRaw)
                .defaultWorkspaceId(defaultWorkspace == null ? null : defaultWorkspace.getId())
                .build();
        auditService.record(AuditAction.AUTH_REFRESH, "SUCCESS", user.getId(), user.getUsername(),
                tokens.getDefaultWorkspaceId(), "token", String.valueOf(user.getId()), Map.of());
        return tokens;
    }

    public void logout(String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenMapper.revokeByHash(hashToken(refreshToken));
        }
        auditService.record(AuditAction.AUTH_LOGOUT, "SUCCESS", null, null, null, "token", null, Map.of());
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null || !STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BizException(401, LOGIN_ERROR);
        }
        if (!passwordService.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(400, "原密码不正确");
        }
        passwordService.validate(request.getNewPassword());
        user.setPasswordHash(passwordService.hash(request.getNewPassword()));
        user.setTokenVersion(user.getTokenVersion() == null ? 1 : user.getTokenVersion() + 1);
        userMapper.updateById(user);
        refreshTokenMapper.revokeAllActive(userId);
    }

    public MeVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, LOGIN_ERROR);
        }
        List<WorkspaceMembershipVO> workspaces = workspaceMemberMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkspaceMember>()
                                .eq(WorkspaceMember::getUserId, userId)
                                .eq(WorkspaceMember::getStatus, STATUS_ACTIVE))
                .stream()
                .map(member -> {
                    Workspace workspace = workspaceMapper.selectById(member.getWorkspaceId());
                    if (workspace == null || !STATUS_ACTIVE.equals(workspace.getStatus())) {
                        return null;
                    }
                    return WorkspaceMembershipVO.builder()
                            .workspaceId(workspace.getId())
                            .code(workspace.getCode())
                            .name(workspace.getName())
                            .role(member.getRoleCode())
                            .memberStatus(member.getStatus())
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return MeVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .status(user.getStatus())
                .workspaces(workspaces)
                .build();
    }

    private AuthTokensVO issueTokens(User user, Long defaultWorkspaceId) {
        String refreshRaw = randomToken();
        persistRefresh(user.getId(), hashToken(refreshRaw));
        return AuthTokensVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .accessToken(jwtTokenService.createAccessToken(user))
                .refreshToken(refreshRaw)
                .defaultWorkspaceId(defaultWorkspaceId)
                .build();
    }

    private void persistRefresh(Long userId, String tokenHash) {
        RefreshToken row = new RefreshToken();
        row.setUserId(userId);
        row.setTokenHash(tokenHash);
        row.setStatus(STATUS_ACTIVE);
        row.setExpiresAt(LocalDateTime.now().plusSeconds(properties.getRefreshTokenTtlSeconds()));
        refreshTokenMapper.insert(row);
    }

    private RefreshToken patchRevoked(RefreshToken stored, String nextHash) {
        stored.setStatus("REVOKED");
        stored.setRevokedAt(LocalDateTime.now());
        stored.setReplacedByTokenHash(nextHash);
        return stored;
    }

    static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hashToken(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("无法哈希 refresh token", e);
        }
    }
}
