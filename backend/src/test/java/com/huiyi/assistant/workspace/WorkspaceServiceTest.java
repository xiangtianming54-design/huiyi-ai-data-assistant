package com.huiyi.assistant.workspace;

import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.knowledge.KnowledgeSpaceService;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.mapper.WorkspaceMapper;
import com.huiyi.assistant.mapper.WorkspaceMemberMapper;
import com.huiyi.assistant.model.entity.User;
import com.huiyi.assistant.model.entity.Workspace;
import com.huiyi.assistant.model.entity.WorkspaceMember;
import com.huiyi.assistant.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock WorkspaceMapper workspaceMapper;
    @Mock WorkspaceMemberMapper workspaceMemberMapper;
    @Mock UserMapper userMapper;
    @Mock AuditService auditService;
    @Mock KnowledgeSpaceService knowledgeSpaceService;

    WorkspaceService workspaceService;
    AuthUser actor;

    @BeforeEach
    void setUp() {
        workspaceService = new WorkspaceService(workspaceMapper, workspaceMemberMapper, userMapper, auditService,
                knowledgeSpaceService);
        actor = new AuthUser(10L, "alice", 0);
    }

    @Test
    void createWorkspaceMakesCreatorOwner() {
        when(workspaceMapper.selectByCode("finance-team")).thenReturn(null);
        when(workspaceMapper.insert(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace workspace = invocation.getArgument(0);
            workspace.setId(2L);
            return 1;
        });
        Workspace created = workspaceService.create(actor, "Finance Team", "finance-team");
        assertEquals(2L, created.getId());
        ArgumentCaptor<WorkspaceMember> member = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(workspaceMemberMapper).insert(member.capture());
        assertEquals("OWNER", member.getValue().getRoleCode());
        assertEquals("ACTIVE", member.getValue().getStatus());
        verify(knowledgeSpaceService).ensureDefault(2L, 10L);
    }

    @Test
    void nonMemberAccessForbidden() {
        User account = new User();
        account.setId(10L);
        account.setStatus("ACTIVE");
        when(userMapper.selectById(10L)).thenReturn(account);
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        workspace.setStatus("ACTIVE");
        when(workspaceMapper.selectById(1L)).thenReturn(workspace);
        when(workspaceMemberMapper.selectByWorkspaceAndUser(1L, 10L)).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> workspaceService.requireMember(actor, 1L));
        assertEquals(403, ex.getCode());
        verify(auditService).accessDenied(10L, "alice", 1L, "不是该工作区的有效成员");
    }

    @Test
    void editorCannotAddMember() {
        stubMember(10L, "EDITOR");
        BizException ex = assertThrows(BizException.class,
                () -> workspaceService.addMember(actor, 1L, "bob", "EDITOR"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void adminCanAddMember() {
        stubMember(10L, "ADMIN");
        User bob = new User();
        bob.setId(20L);
        bob.setUsername("bob");
        bob.setStatus("ACTIVE");
        when(userMapper.selectByUsername("bob")).thenReturn(bob);
        when(workspaceMemberMapper.selectByWorkspaceAndUser(1L, 20L)).thenReturn(null);
        workspaceService.addMember(actor, 1L, "bob", "EDITOR");
        verify(workspaceMemberMapper).insert(any(WorkspaceMember.class));
    }

    @Test
    void adminCannotChangeOwner() {
        stubMember(10L, "ADMIN");
        WorkspaceMember owner = member(1L, 1L, "OWNER", "ACTIVE");
        when(workspaceMemberMapper.selectById(1L)).thenReturn(owner);
        BizException ex = assertThrows(BizException.class,
                () -> workspaceService.changeRole(actor, 1L, 1L, "ADMIN"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void cannotSuspendLastActiveOwner() {
        stubMember(10L, "OWNER");
        WorkspaceMember owner = member(1L, 10L, "OWNER", "ACTIVE");
        owner.setId(8L);
        when(workspaceMemberMapper.selectById(8L)).thenReturn(owner);
        when(workspaceMemberMapper.selectCount(any())).thenReturn(1L);
        BizException ex = assertThrows(BizException.class,
                () -> workspaceService.changeStatus(actor, 1L, 8L, "SUSPENDED"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void reAddRestoresActive() {
        stubMember(10L, "ADMIN");
        User bob = new User();
        bob.setId(20L);
        bob.setUsername("bob");
        bob.setStatus("ACTIVE");
        when(userMapper.selectByUsername("bob")).thenReturn(bob);
        WorkspaceMember revoked = member(1L, 20L, "VIEWER", "REVOKED");
        revoked.setId(9L);
        when(workspaceMemberMapper.selectByWorkspaceAndUser(1L, 20L)).thenReturn(revoked);
        workspaceService.addMember(actor, 1L, "bob", "EDITOR");
        assertEquals("ACTIVE", revoked.getStatus());
        assertEquals("EDITOR", revoked.getRoleCode());
        verify(workspaceMemberMapper).updateById(revoked);
    }

    private void stubMember(Long userId, String role) {
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        workspace.setStatus("ACTIVE");
        when(workspaceMapper.selectById(1L)).thenReturn(workspace);
        when(workspaceMemberMapper.selectByWorkspaceAndUser(1L, userId)).thenReturn(member(1L, userId, role, "ACTIVE"));
        User user = new User();
        user.setId(userId);
        user.setStatus("ACTIVE");
        when(userMapper.selectById(userId)).thenReturn(user);
    }

    private WorkspaceMember member(Long workspaceId, Long userId, String role, String status) {
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRoleCode(role);
        member.setStatus(status);
        return member;
    }
}
