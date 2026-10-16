package com.huiyi.assistant.workspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huiyi.assistant.auth.AuthService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.mapper.WorkspaceMapper;
import com.huiyi.assistant.mapper.WorkspaceMemberMapper;
import com.huiyi.assistant.model.entity.User;
import com.huiyi.assistant.model.entity.Workspace;
import com.huiyi.assistant.model.entity.WorkspaceMember;
import com.huiyi.assistant.model.vo.MemberVO;
import com.huiyi.assistant.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class WorkspaceService {

    private static final Set<String> ROLES = Set.of("OWNER", "ADMIN", "EDITOR", "VIEWER");
    private static final Set<String> MEMBER_STATUSES = Set.of("ACTIVE", "SUSPENDED", "REVOKED", "LEFT", "ARCHIVED");

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final UserMapper userMapper;

    public WorkspaceService(WorkspaceMapper workspaceMapper, WorkspaceMemberMapper workspaceMemberMapper,
                            UserMapper userMapper) {
        this.workspaceMapper = workspaceMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.userMapper = userMapper;
    }

    public WorkspaceContext requireMember(AuthUser user, Long workspaceId) {
        User account = userMapper.selectById(user.userId());
        if (account == null || !AuthService.STATUS_ACTIVE.equals(account.getStatus())) {
            throw new BizException(401, AuthService.LOGIN_ERROR);
        }
        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null || !AuthService.STATUS_ACTIVE.equals(workspace.getStatus())) {
            throw new BizException(403, "工作区不可用");
        }
        WorkspaceMember member = workspaceMemberMapper.selectByWorkspaceAndUser(workspaceId, user.userId());
        if (member == null || !AuthService.STATUS_ACTIVE.equals(member.getStatus())) {
            throw new BizException(403, "不是该工作区的有效成员");
        }
        return new WorkspaceContext(user.userId(), workspaceId, member.getId(), member.getRoleCode(),
                member.getStatus(), account.getStatus());
    }

    public WorkspaceContext requireManageMembers(AuthUser user, Long workspaceId) {
        WorkspaceContext context = requireMember(user, workspaceId);
        if (!context.canManageMembers()) {
            throw new BizException(403, "没有权限管理成员");
        }
        return context;
    }

    public WorkspaceContext requireKnowledgeWrite(AuthUser user, Long workspaceId) {
        WorkspaceContext context = requireMember(user, workspaceId);
        if (!context.canUploadKnowledge()) {
            throw new BizException(403, "只读成员不能上传或归档知识库");
        }
        return context;
    }

    @Transactional
    public Workspace create(AuthUser user, String name, String code) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(code)) {
            throw new BizException(400, "工作区名称和编码不能为空");
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (workspaceMapper.selectByCode(normalized) != null) {
            throw new BizException(400, "工作区编码已存在");
        }
        Workspace workspace = new Workspace();
        workspace.setName(name.trim());
        workspace.setCode(normalized);
        workspace.setOwnerId(user.userId());
        workspace.setStatus(AuthService.STATUS_ACTIVE);
        workspaceMapper.insert(workspace);
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspace.getId());
        member.setUserId(user.userId());
        member.setRoleCode("OWNER");
        member.setStatus(AuthService.STATUS_ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        workspaceMemberMapper.insert(member);
        return workspace;
    }

    public Workspace get(AuthUser user, Long workspaceId) {
        requireMember(user, workspaceId);
        return workspaceMapper.selectById(workspaceId);
    }

    public List<Workspace> listMine(AuthUser user) {
        List<WorkspaceMember> memberships = workspaceMemberMapper.selectList(
                new LambdaQueryWrapper<WorkspaceMember>()
                        .eq(WorkspaceMember::getUserId, user.userId())
                        .eq(WorkspaceMember::getStatus, AuthService.STATUS_ACTIVE));
        return memberships.stream()
                .map(member -> workspaceMapper.selectById(member.getWorkspaceId()))
                .filter(workspace -> workspace != null && AuthService.STATUS_ACTIVE.equals(workspace.getStatus()))
                .toList();
    }

    public List<MemberVO> listMembers(AuthUser user, Long workspaceId, String status, String role) {
        requireMember(user, workspaceId);
        LambdaQueryWrapper<WorkspaceMember> wrapper = new LambdaQueryWrapper<WorkspaceMember>()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(WorkspaceMember::getStatus, status);
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(WorkspaceMember::getRoleCode, role);
        }
        return workspaceMemberMapper.selectList(wrapper).stream()
                .map(member -> toMemberVO(member))
                .filter(vo -> vo != null && !"migrated".equals(vo.getUsername()))
                .toList();
    }

    @Transactional
    public void addMember(AuthUser user, Long workspaceId, String username, String role) {
        WorkspaceContext context = requireManageMembers(user, workspaceId);
        String roleCode = normalizeRole(role);
        if ("OWNER".equals(roleCode) && !"OWNER".equals(context.roleCode())) {
            throw new BizException(403, "只有 OWNER 可以指定新的 OWNER");
        }
        User target = userMapper.selectByUsername(username);
        if (target == null || !AuthService.STATUS_ACTIVE.equals(target.getStatus())) {
            throw new BizException(400, "用户不存在或不可用");
        }
        WorkspaceMember existing = workspaceMemberMapper.selectByWorkspaceAndUser(workspaceId, target.getId());
        if (existing == null) {
            WorkspaceMember member = new WorkspaceMember();
            member.setWorkspaceId(workspaceId);
            member.setUserId(target.getId());
            member.setRoleCode(roleCode);
            member.setStatus(AuthService.STATUS_ACTIVE);
            member.setJoinedAt(LocalDateTime.now());
            member.setChangedBy(user.userId());
            workspaceMemberMapper.insert(member);
            return;
        }
        existing.setStatus(AuthService.STATUS_ACTIVE);
        existing.setRoleCode(roleCode);
        existing.setChangedAt(LocalDateTime.now());
        existing.setChangedBy(user.userId());
        workspaceMemberMapper.updateById(existing);
    }

    @Transactional
    public void changeRole(AuthUser user, Long workspaceId, Long memberId, String role) {
        WorkspaceContext context = requireManageMembers(user, workspaceId);
        WorkspaceMember target = loadMember(workspaceId, memberId);
        String roleCode = normalizeRole(role);
        if ("OWNER".equals(target.getRoleCode()) && !"OWNER".equals(context.roleCode())) {
            throw new BizException(403, "ADMIN 不能修改 OWNER");
        }
        if ("OWNER".equals(roleCode) && !"OWNER".equals(context.roleCode())) {
            throw new BizException(403, "只有 OWNER 可以指定新的 OWNER");
        }
        if ("OWNER".equals(target.getRoleCode()) && !"OWNER".equals(roleCode)
                && countActiveOwners(workspaceId) <= 1) {
            throw new BizException(400, "不能让工作区失去最后一个 ACTIVE OWNER");
        }
        target.setRoleCode(roleCode);
        target.setChangedAt(LocalDateTime.now());
        target.setChangedBy(user.userId());
        workspaceMemberMapper.updateById(target);
    }

    @Transactional
    public void changeStatus(AuthUser user, Long workspaceId, Long memberId, String status) {
        WorkspaceContext context = requireManageMembers(user, workspaceId);
        WorkspaceMember target = loadMember(workspaceId, memberId);
        String next = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!MEMBER_STATUSES.contains(next)) {
            throw new BizException(400, "不支持的成员状态");
        }
        if ("OWNER".equals(target.getRoleCode()) && !"OWNER".equals(context.roleCode())) {
            throw new BizException(403, "ADMIN 不能停用 OWNER");
        }
        if ("OWNER".equals(target.getRoleCode()) && AuthService.STATUS_ACTIVE.equals(target.getStatus())
                && !AuthService.STATUS_ACTIVE.equals(next) && countActiveOwners(workspaceId) <= 1) {
            throw new BizException(400, "不能停用最后一个 ACTIVE OWNER");
        }
        target.setStatus(next);
        target.setChangedAt(LocalDateTime.now());
        target.setChangedBy(user.userId());
        workspaceMemberMapper.updateById(target);
    }

    private long countActiveOwners(Long workspaceId) {
        return workspaceMemberMapper.selectCount(new LambdaQueryWrapper<WorkspaceMember>()
                .eq(WorkspaceMember::getWorkspaceId, workspaceId)
                .eq(WorkspaceMember::getRoleCode, "OWNER")
                .eq(WorkspaceMember::getStatus, AuthService.STATUS_ACTIVE));
    }

    private WorkspaceMember loadMember(Long workspaceId, Long memberId) {
        WorkspaceMember member = workspaceMemberMapper.selectById(memberId);
        if (member == null || !workspaceId.equals(member.getWorkspaceId())) {
            throw new BizException(404, "成员不存在");
        }
        return member;
    }

    private String normalizeRole(String role) {
        String roleCode = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(roleCode)) {
            throw new BizException(400, "不支持的角色");
        }
        return roleCode;
    }

    private MemberVO toMemberVO(WorkspaceMember member) {
        User user = userMapper.selectById(member.getUserId());
        if (user == null) {
            return null;
        }
        return MemberVO.builder()
                .memberId(member.getId())
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .userStatus(user.getStatus())
                .memberStatus(member.getStatus())
                .role(member.getRoleCode())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
