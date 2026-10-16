package com.huiyi.assistant.workspace;

import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.mapper.WorkspaceMapper;
import com.huiyi.assistant.mapper.WorkspaceMemberMapper;
import com.huiyi.assistant.model.entity.User;
import com.huiyi.assistant.model.entity.Workspace;
import com.huiyi.assistant.model.entity.WorkspaceMember;
import com.huiyi.assistant.model.vo.MemberVO;
import com.huiyi.assistant.model.vo.PageResult;
import com.huiyi.assistant.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberPageTest {

    @Mock WorkspaceMapper workspaceMapper;
    @Mock WorkspaceMemberMapper workspaceMemberMapper;
    @Mock UserMapper userMapper;
    @Mock AuditService auditService;

    WorkspaceService workspaceService;
    AuthUser actor;

    @BeforeEach
    void setUp() {
        workspaceService = new WorkspaceService(workspaceMapper, workspaceMemberMapper, userMapper, auditService);
        actor = new AuthUser(10L, "alice", 0);
        User account = new User();
        account.setId(10L);
        account.setStatus("ACTIVE");
        when(userMapper.selectById(10L)).thenReturn(account);
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        workspace.setStatus("ACTIVE");
        when(workspaceMapper.selectById(1L)).thenReturn(workspace);
        WorkspaceMember self = new WorkspaceMember();
        self.setWorkspaceId(1L);
        self.setUserId(10L);
        self.setRoleCode("OWNER");
        self.setStatus("ACTIVE");
        when(workspaceMemberMapper.selectByWorkspaceAndUser(1L, 10L)).thenReturn(self);
    }

    @Test
    void listMembersPaginatesAndFiltersKeyword() {
        when(workspaceMemberMapper.selectList(any())).thenReturn(List.of(
                member(1L, 10L, "OWNER"),
                member(2L, 20L, "EDITOR"),
                member(3L, 30L, "VIEWER")));
        stubUser(10L, "alice", "Alice");
        stubUser(20L, "bob", "Bob");
        stubUser(30L, "carol", "Carol");

        PageResult<MemberVO> page = workspaceService.listMembers(actor, 1L, 1, 2, null, null, null);

        assertEquals(1, page.getPage());
        assertEquals(2, page.getPageSize());
        assertEquals(3, page.getTotal());
        assertEquals(2, page.getItems().size());
        assertEquals("alice", page.getItems().get(0).getUsername());
        assertEquals("bob", page.getItems().get(1).getUsername());

        PageResult<MemberVO> filtered = workspaceService.listMembers(actor, 1L, 1, 20, null, null, "bob");
        assertEquals(1, filtered.getTotal());
        assertEquals("bob", filtered.getItems().get(0).getUsername());
    }

    private WorkspaceMember member(Long id, Long userId, String role) {
        WorkspaceMember member = new WorkspaceMember();
        member.setId(id);
        member.setWorkspaceId(1L);
        member.setUserId(userId);
        member.setRoleCode(role);
        member.setStatus("ACTIVE");
        return member;
    }

    private void stubUser(Long id, String username, String displayName) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setStatus("ACTIVE");
        when(userMapper.selectById(id)).thenReturn(user);
    }
}
