package com.huiyi.assistant.api.v1;

import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.knowledge.KnowledgeSpaceService;
import com.huiyi.assistant.model.dto.ChangeStatusRequest;
import com.huiyi.assistant.model.dto.KnowledgeSpaceWriteRequest;
import com.huiyi.assistant.model.entity.KnowledgeSpace;
import com.huiyi.assistant.security.AuthUser;
import com.huiyi.assistant.workspace.WorkspaceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/knowledge-spaces")
public class KnowledgeSpaceV1Controller {

    private final WorkspaceService workspaceService;
    private final KnowledgeSpaceService knowledgeSpaceService;

    public KnowledgeSpaceV1Controller(WorkspaceService workspaceService, KnowledgeSpaceService knowledgeSpaceService) {
        this.workspaceService = workspaceService;
        this.knowledgeSpaceService = knowledgeSpaceService;
    }

    @PostMapping
    public Result<KnowledgeSpace> create(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                         @RequestBody KnowledgeSpaceWriteRequest request) {
        WorkspaceContext context = workspaceService.requireKnowledgeWrite(user, workspaceId);
        String name = request == null ? null : request.getName();
        String description = request == null ? null : request.getDescription();
        return Result.ok(knowledgeSpaceService.create(context.workspaceId(), context.userId(), name, description));
    }

    @GetMapping
    public Result<List<KnowledgeSpace>> list(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId) {
        WorkspaceContext context = workspaceService.requireMember(user, workspaceId);
        return Result.ok(knowledgeSpaceService.listActive(context.workspaceId()));
    }

    @PatchMapping("/{spaceId}")
    public Result<KnowledgeSpace> update(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                         @PathVariable Long spaceId, @RequestBody KnowledgeSpaceWriteRequest request) {
        WorkspaceContext context = workspaceService.requireKnowledgeWrite(user, workspaceId);
        String name = request == null ? null : request.getName();
        String description = request == null ? null : request.getDescription();
        return Result.ok(knowledgeSpaceService.update(context.workspaceId(), spaceId, name, description,
                context.userId()));
    }

    @PatchMapping("/{spaceId}/status")
    public Result<Void> archive(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                @PathVariable Long spaceId, @RequestBody ChangeStatusRequest request) {
        WorkspaceContext context = workspaceService.requireKnowledgeWrite(user, workspaceId);
        if (request != null && "ARCHIVED".equalsIgnoreCase(request.getStatus())) {
            knowledgeSpaceService.archive(context.workspaceId(), spaceId, context.userId());
        }
        return Result.ok();
    }
}
