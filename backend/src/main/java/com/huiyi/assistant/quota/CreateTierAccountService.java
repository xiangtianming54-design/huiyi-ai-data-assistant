package com.huiyi.assistant.quota;

import com.huiyi.assistant.auth.AuthService;
import com.huiyi.assistant.auth.PasswordService;
import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.mapper.WorkspaceMemberMapper;
import com.huiyi.assistant.model.dto.CreateTierAccountRequest;
import com.huiyi.assistant.model.entity.User;
import com.huiyi.assistant.model.entity.WorkspaceMember;
import com.huiyi.assistant.model.vo.CreatedUserVO;
import com.huiyi.assistant.security.AuthUser;
import com.huiyi.assistant.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 第三级在已加入的工作区新建第二级或第三级账号，并写成 VIEWER。
 *
 * @author huiyi
 */
@Service
public class CreateTierAccountService {

    private final PasswordService passwordService;
    private final UserMapper userMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final WorkspaceService workspaceService;

    public CreateTierAccountService(PasswordService passwordService, UserMapper userMapper,
                                    WorkspaceMemberMapper workspaceMemberMapper, WorkspaceService workspaceService) {
        this.passwordService = passwordService;
        this.userMapper = userMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.workspaceService = workspaceService;
    }

    /**
     * 创建者必须已是该工作区成员，且等级为 3。不修改已有用户的等级或角色。
     *
     * @param creator     当前登录用户
     * @param workspaceId 路径上的工作区
     * @param request     用户名、口令、可空显示名、等级
     * @return 新用户编号、用户名和等级
     */
    @Transactional
    public CreatedUserVO create(AuthUser creator, long workspaceId, CreateTierAccountRequest request) {
        workspaceService.requireMember(creator, workspaceId);
        User actor = userMapper.selectById(creator.userId());
        if (actor == null || actor.getUserTier() == null || actor.getUserTier() != 3) {
            throw new BizException(403, "只有第三级可以新建账号");
        }
        Integer tier = request.getTier();
        if (tier == null || (tier != 2 && tier != 3)) {
            throw new BizException(400, "等级只能是第二级或第三级");
        }
        passwordService.validate(request.getPassword());
        if (!StringUtils.hasText(request.getUsername()) || request.getUsername().length() < 3) {
            throw new BizException(400, "用户名至少 3 位");
        }
        if (userMapper.selectByUsername(request.getUsername()) != null) {
            throw new BizException(400, "用户名已被占用");
        }
        String username = request.getUsername().trim();
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(StringUtils.hasText(request.getDisplayName())
                ? request.getDisplayName().trim()
                : username);
        user.setPasswordHash(passwordService.hash(request.getPassword()));
        user.setStatus(AuthService.STATUS_ACTIVE);
        user.setTokenVersion(0);
        user.setUserTier(tier);
        userMapper.insert(user);

        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setUserId(user.getId());
        member.setRoleCode("VIEWER");
        member.setStatus(AuthService.STATUS_ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        workspaceMemberMapper.insert(member);
        return new CreatedUserVO(user.getId(), user.getUsername(), tier);
    }
}
