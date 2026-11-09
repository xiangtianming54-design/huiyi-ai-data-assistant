package com.huiyi.assistant.quota;

import com.huiyi.assistant.api.v1.TierAccountController;
import com.huiyi.assistant.auth.PasswordService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.mapper.WorkspaceMemberMapper;
import com.huiyi.assistant.model.dto.CreateTierAccountRequest;
import com.huiyi.assistant.model.entity.User;
import com.huiyi.assistant.model.entity.WorkspaceMember;
import com.huiyi.assistant.model.vo.CreatedUserVO;
import com.huiyi.assistant.security.AuthUser;
import com.huiyi.assistant.workspace.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 第三级在自己所属的工作区新建第二级或第三级账号。
 */
@ExtendWith(MockitoExtension.class)
class CreateTierAccountServiceTest {

    private static final long WORKSPACE_ID = 42L;
    private static final long BLANK_ID = 16L;
    private static final long LIMIT_ID = 17L;
    private static final long TIER2_ID = 18L;
    private static final long TIER1_ID = 19L;
    private static final long TIER3_ID = 20L;
    private static final long UNTIERED_ID = 21L;
    private static final String PASSWORD = "strong-password";

    @Mock UserMapper userMapper;
    @Mock WorkspaceMemberMapper workspaceMemberMapper;
    @Mock WorkspaceService workspaceService;

    private final List<User> people = new ArrayList<>();
    private final List<WorkspaceMember> memberships = new ArrayList<>();

    private PasswordService passwordService;
    private CreateTierAccountService service;

    @BeforeEach
    void setUp() {
        people.clear();
        memberships.clear();
        passwordService = spy(new PasswordService());
        service = new CreateTierAccountService(passwordService, userMapper, workspaceMemberMapper, workspaceService);
        remember(BLANK_ID, "blank", 3);
        remember(LIMIT_ID, "limit", 2);
        remember(TIER2_ID, "side", 2);
        remember(TIER1_ID, "ada", 1);
        remember(TIER3_ID, "owner", 3);
        remember(UNTIERED_ID, "unset", null);
        member(BLANK_ID, "VIEWER");
        member(LIMIT_ID, "VIEWER");
        member(TIER2_ID, "VIEWER");
        member(TIER1_ID, "VIEWER");
        member(TIER3_ID, "OWNER");
        lenient().when(userMapper.selectById(any())).thenAnswer(invocation -> {
            Number id = invocation.getArgument(0);
            return people.stream().filter(user -> id.longValue() == user.getId()).findFirst().orElse(null);
        });
        lenient().when(userMapper.selectByUsername(any())).thenAnswer(invocation -> {
            String username = invocation.getArgument(0);
            return people.stream().filter(user -> username.equals(user.getUsername())).findFirst().orElse(null);
        });
        lenient().when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(100L + people.size());
            people.add(user);
            return 1;
        });
        lenient().when(workspaceMemberMapper.insert(any(WorkspaceMember.class))).thenAnswer(invocation -> {
            memberships.add(invocation.getArgument(0));
            return 1;
        });
        lenient().when(workspaceMemberMapper.selectByWorkspaceAndUser(any(), any())).thenAnswer(invocation -> {
            Long workspaceId = invocation.getArgument(0);
            Long userId = invocation.getArgument(1);
            return memberships.stream()
                    .filter(row -> workspaceId.equals(row.getWorkspaceId()) && userId.equals(row.getUserId()))
                    .findFirst()
                    .orElse(null);
        });
        lenient().when(workspaceService.requireMember(any(), any())).thenAnswer(invocation -> {
            AuthUser user = invocation.getArgument(0);
            Long workspaceId = invocation.getArgument(1);
            return new WorkspaceContext(user.userId(), workspaceId, 9L, "OWNER", "ACTIVE", "ACTIVE");
        });
    }

    @Test
    void tierTwoCallerIsForbidden() {
        BizException ex = assertThrows(BizException.class,
                () -> service.create(new AuthUser(TIER2_ID, "side", 0), WORKSPACE_ID,
                        request("clerk", PASSWORD, "Clerk", 2)));

        assertEquals(403, ex.getCode());
        assertEquals("只有第三级可以新建账号", ex.getMessage());
        verify(userMapper, never()).insert(any(User.class));
        assertUntouchedAccounts();
    }

    @Test
    void tierOneCallerIsForbidden() {
        BizException ex = assertThrows(BizException.class,
                () -> service.create(new AuthUser(TIER1_ID, "ada", 0), WORKSPACE_ID,
                        request("clerk", PASSWORD, null, 3)));

        assertEquals(403, ex.getCode());
        assertEquals("只有第三级可以新建账号", ex.getMessage());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void callerWithoutATierIsForbidden() {
        BizException ex = assertThrows(BizException.class,
                () -> service.create(new AuthUser(UNTIERED_ID, "unset", 0), WORKSPACE_ID,
                        request("clerk", PASSWORD, null, 2)));

        assertEquals(403, ex.getCode());
        assertEquals("只有第三级可以新建账号", ex.getMessage());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void tierOneAccountIsRejected() {
        for (Integer tier : new Integer[] {null, 0, 1, 4}) {
            BizException ex = assertThrows(BizException.class,
                    () -> service.create(tier3(), WORKSPACE_ID, request("clerk", PASSWORD, "Clerk", tier)));
            assertEquals(400, ex.getCode());
            assertEquals("等级只能是第二级或第三级", ex.getMessage());
        }
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void tierThreeCreatesTierTwoViewerAndDoesNotUpdateCreatorRole() {
        CreatedUserVO created = service.create(tier3(), WORKSPACE_ID, request("clerk", PASSWORD, "  前台  ", 2));

        ArgumentCaptor<User> insertedUser = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(insertedUser.capture());
        User user = insertedUser.getValue();
        assertEquals(2, user.getUserTier());
        assertEquals("ACTIVE", user.getStatus());
        assertEquals(0, user.getTokenVersion());
        assertEquals("clerk", user.getUsername());
        assertEquals("前台", user.getDisplayName());
        assertTrue(passwordService.matches(PASSWORD, user.getPasswordHash()));
        assertEquals(new CreatedUserVO(user.getId(), "clerk", 2), created);

        ArgumentCaptor<WorkspaceMember> insertedMember = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(workspaceMemberMapper).insert(insertedMember.capture());
        WorkspaceMember member = insertedMember.getValue();
        assertEquals("VIEWER", member.getRoleCode());
        assertEquals(WORKSPACE_ID, member.getWorkspaceId());
        assertEquals("ACTIVE", member.getStatus());
        assertEquals(user.getId(), member.getUserId());
        assertNotNull(member.getJoinedAt());

        verify(workspaceMemberMapper, times(0)).updateById(any(WorkspaceMember.class));
        verify(userMapper, times(0)).updateById(any(User.class));
        assertEquals("OWNER", storedMember(TIER3_ID).getRoleCode());
        assertUntouchedAccounts();

        InOrder gate = inOrder(workspaceService, userMapper);
        gate.verify(workspaceService).requireMember(tier3(), WORKSPACE_ID);
        gate.verify(userMapper).insert(any(User.class));
        InOrder passwords = inOrder(passwordService);
        passwords.verify(passwordService).validate(PASSWORD);
        passwords.verify(passwordService).hash(PASSWORD);
    }

    @Test
    void tierThreeCreatesTierThreeAccount() {
        CreatedUserVO created = service.create(tier3(), WORKSPACE_ID, request("bob", PASSWORD, "Bob", 3));

        ArgumentCaptor<User> insertedUser = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(insertedUser.capture());
        assertEquals(3, insertedUser.getValue().getUserTier());
        assertEquals("bob", insertedUser.getValue().getUsername());
        assertEquals("ACTIVE", insertedUser.getValue().getStatus());
        assertEquals(new CreatedUserVO(insertedUser.getValue().getId(), "bob", 3), created);
        ArgumentCaptor<WorkspaceMember> insertedMember = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(workspaceMemberMapper).insert(insertedMember.capture());
        assertEquals("VIEWER", insertedMember.getValue().getRoleCode());
        assertEquals(WORKSPACE_ID, insertedMember.getValue().getWorkspaceId());
    }

    @Test
    void shortPasswordUsesExistingPolicy() {
        BizException ex = assertThrows(BizException.class,
                () -> service.create(tier3(), WORKSPACE_ID, request("clerk", "short", null, 2)));

        assertEquals(400, ex.getCode());
        assertEquals("密码至少 10 位", ex.getMessage());
        verify(passwordService, never()).hash(any());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void weakPasswordUsesExistingPolicy() {
        BizException ex = assertThrows(BizException.class,
                () -> service.create(tier3(), WORKSPACE_ID, request("clerk", "password123", null, 2)));

        assertEquals(400, ex.getCode());
        assertEquals("密码过于简单", ex.getMessage());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void usernameShorterThanThreeIsRejected() {
        for (String username : new String[] {null, "", "  ", "ab"}) {
            BizException ex = assertThrows(BizException.class,
                    () -> service.create(tier3(), WORKSPACE_ID, request(username, PASSWORD, null, 2)));
            assertEquals(400, ex.getCode());
            assertEquals("用户名至少 3 位", ex.getMessage());
        }
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void duplicateUsernameIsRejected() {
        BizException ex = assertThrows(BizException.class,
                () -> service.create(tier3(), WORKSPACE_ID, request("blank", PASSWORD, null, 2)));

        assertEquals(400, ex.getCode());
        assertEquals("用户名已被占用", ex.getMessage());
        ex = assertThrows(BizException.class,
                () -> service.create(tier3(), WORKSPACE_ID, request("limit", PASSWORD, null, 3)));
        assertEquals(400, ex.getCode());
        assertEquals("用户名已被占用", ex.getMessage());
        verify(userMapper, never()).insert(any(User.class));
        verify(userMapper, never()).updateById(any(User.class));
        assertUntouchedAccounts();
    }

    @Test
    void blankDisplayNameIsStoredAsTheUsername() {
        String[] displayNames = {null, "", "   ", "\t"};
        String[] usernames = {"neo", "nia", "ned", "nan"};
        for (int i = 0; i < displayNames.length; i++) {
            service.create(tier3(), WORKSPACE_ID, request(usernames[i], PASSWORD, displayNames[i], 2));
        }

        ArgumentCaptor<User> insertedUser = ArgumentCaptor.forClass(User.class);
        verify(userMapper, times(4)).insert(insertedUser.capture());
        List<User> inserted = insertedUser.getAllValues();
        for (int i = 0; i < usernames.length; i++) {
            assertEquals(usernames[i], inserted.get(i).getUsername());
            assertEquals(usernames[i], inserted.get(i).getDisplayName());
        }
    }

    @Test
    void nonMemberIsNotInserted() {
        doThrow(new BizException(403, "不是该工作区的有效成员"))
                .when(workspaceService).requireMember(any(), any());

        BizException ex = assertThrows(BizException.class,
                () -> service.create(tier3(), WORKSPACE_ID, request("clerk", PASSWORD, null, 2)));

        assertEquals(403, ex.getCode());
        assertEquals("不是该工作区的有效成员", ex.getMessage());
        verify(userMapper, never()).insert(any(User.class));
        verify(workspaceMemberMapper, never()).insert(any(WorkspaceMember.class));
    }

    @Test
    void createDoesNotExposeATierChangeMethod() {
        for (Method method : CreateTierAccountService.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            String name = method.getName();
            assertFalse(name.contains("updateTier"), name);
            assertFalse(name.contains("changeTier"), name);
            assertFalse(name.contains("promote"), name);
        }
    }

    @Test
    void controllerPostsCreatedUserWithoutAToken() throws Exception {
        TierAccountController controller = new TierAccountController(service);
        Method method = TierAccountController.class.getMethod(
                "create", AuthUser.class, long.class, CreateTierAccountRequest.class);
        assertEquals("/api/v1/workspaces/{workspaceId}/users", route(method));
        assertTrue(method.getParameters()[0].isAnnotationPresent(AuthenticationPrincipal.class));
        assertEquals(List.of("id", "username", "tier"), componentNames(CreatedUserVO.class));

        Result<CreatedUserVO> result = controller.create(
                tier3(), WORKSPACE_ID, request("clerk", PASSWORD, null, 2));

        assertEquals(0, result.getCode());
        assertEquals("clerk", result.getData().username());
        assertEquals(2, result.getData().tier());
        ArgumentCaptor<WorkspaceMember> insertedMember = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(workspaceMemberMapper).insert(insertedMember.capture());
        assertEquals(WORKSPACE_ID, insertedMember.getValue().getWorkspaceId());
        assertEquals("VIEWER", insertedMember.getValue().getRoleCode());
        assertEquals("ACTIVE", insertedMember.getValue().getStatus());
        assertUntouchedAccounts();
    }

    private void assertUntouchedAccounts() {
        assertEquals(3, storedUser(BLANK_ID).getUserTier());
        assertEquals(2, storedUser(LIMIT_ID).getUserTier());
        assertEquals("VIEWER", storedMember(BLANK_ID).getRoleCode());
        assertEquals("VIEWER", storedMember(LIMIT_ID).getRoleCode());
        assertEquals("OWNER", storedMember(TIER3_ID).getRoleCode());
    }

    private AuthUser tier3() {
        return new AuthUser(TIER3_ID, "owner", 0);
    }

    private CreateTierAccountRequest request(String username, String password, String displayName, Integer tier) {
        return new CreateTierAccountRequest(username, password, displayName, tier);
    }

    private void remember(long id, String username, Integer tier) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setUserTier(tier);
        user.setStatus("ACTIVE");
        people.add(user);
    }

    private void member(long userId, String role) {
        WorkspaceMember row = new WorkspaceMember();
        row.setWorkspaceId(WORKSPACE_ID);
        row.setUserId(userId);
        row.setRoleCode(role);
        row.setStatus("ACTIVE");
        memberships.add(row);
    }

    private User storedUser(long id) {
        return people.stream().filter(user -> id == user.getId()).findFirst().orElseThrow();
    }

    private WorkspaceMember storedMember(long userId) {
        return memberships.stream().filter(row -> userId == row.getUserId()).findFirst().orElseThrow();
    }

    private static List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static String route(Method method) {
        RequestMapping root = method.getDeclaringClass().getAnnotation(RequestMapping.class);
        String prefix = root == null || root.value().length == 0 ? "" : root.value()[0];
        PostMapping post = method.getAnnotation(PostMapping.class);
        return prefix + post.value()[0];
    }
}
