package com.huiyi.assistant.api.v1;

import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.model.dto.AddMemberRequest;
import com.huiyi.assistant.model.dto.ChangeRoleRequest;
import com.huiyi.assistant.model.dto.ChangeStatusRequest;
import com.huiyi.assistant.model.dto.CreateWorkspaceRequest;
import com.huiyi.assistant.model.entity.Workspace;
import com.huiyi.assistant.model.vo.MemberVO;
import com.huiyi.assistant.model.vo.PageResult;
import com.huiyi.assistant.security.AuthUser;
import com.huiyi.assistant.workspace.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    public Result<Workspace> create(@AuthenticationPrincipal AuthUser user,
                                    @Valid @RequestBody CreateWorkspaceRequest request) {
        return Result.ok(workspaceService.create(user, request.getName(), request.getCode()));
    }

    @GetMapping
    public Result<List<Workspace>> list(@AuthenticationPrincipal AuthUser user) {
        return Result.ok(workspaceService.listMine(user));
    }

    @GetMapping("/{workspaceId}")
    public Result<Workspace> detail(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId) {
        return Result.ok(workspaceService.get(user, workspaceId));
    }

    @GetMapping("/{workspaceId}/members")
    public Result<PageResult<MemberVO>> members(@AuthenticationPrincipal AuthUser user,
                                                @PathVariable Long workspaceId,
                                                @RequestParam(required = false, defaultValue = "1") Integer page,
                                                @RequestParam(required = false, defaultValue = "20") Integer pageSize,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String role) {
        return Result.ok(workspaceService.listMembers(user, workspaceId, page, pageSize, status, role, keyword));
    }

    @PostMapping("/{workspaceId}/members")
    public Result<Void> addMember(@AuthenticationPrincipal AuthUser user,
                                  @PathVariable Long workspaceId,
                                  @Valid @RequestBody AddMemberRequest request) {
        workspaceService.addMember(user, workspaceId, request.getUsername(), request.getRole());
        return Result.ok();
    }

    @PatchMapping("/{workspaceId}/members/{memberId}/role")
    public Result<Void> changeRole(@AuthenticationPrincipal AuthUser user,
                                   @PathVariable Long workspaceId,
                                   @PathVariable Long memberId,
                                   @Valid @RequestBody ChangeRoleRequest request) {
        workspaceService.changeRole(user, workspaceId, memberId, request.getRole());
        return Result.ok();
    }

    @PatchMapping("/{workspaceId}/members/{memberId}/status")
    public Result<Void> changeStatus(@AuthenticationPrincipal AuthUser user,
                                     @PathVariable Long workspaceId,
                                     @PathVariable Long memberId,
                                     @Valid @RequestBody ChangeStatusRequest request) {
        workspaceService.changeStatus(user, workspaceId, memberId, request.getStatus());
        return Result.ok();
    }
}
