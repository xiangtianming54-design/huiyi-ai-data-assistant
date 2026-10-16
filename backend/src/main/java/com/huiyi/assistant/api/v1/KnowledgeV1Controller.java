package com.huiyi.assistant.api.v1;

import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.model.dto.ChangeStatusRequest;
import com.huiyi.assistant.model.vo.KnowledgeDocVO;
import com.huiyi.assistant.model.vo.PageResult;
import com.huiyi.assistant.security.AuthUser;
import com.huiyi.assistant.service.KnowledgeDocService;
import com.huiyi.assistant.workspace.WorkspaceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/knowledge")
public class KnowledgeV1Controller {

    private final WorkspaceService workspaceService;
    private final KnowledgeDocService knowledgeDocService;

    public KnowledgeV1Controller(WorkspaceService workspaceService, KnowledgeDocService knowledgeDocService) {
        this.workspaceService = workspaceService;
        this.knowledgeDocService = knowledgeDocService;
    }

    @PostMapping("/upload")
    public Result<KnowledgeDocVO> upload(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                         @RequestParam("file") MultipartFile file) {
        WorkspaceContext context = workspaceService.requireKnowledgeWrite(user, workspaceId);
        return Result.ok(knowledgeDocService.upload(file, context.workspaceId(), context.userId()));
    }

    @GetMapping("/docs")
    public Result<PageResult<KnowledgeDocVO>> list(@AuthenticationPrincipal AuthUser user,
                                                   @PathVariable Long workspaceId,
                                                   @RequestParam(required = false, defaultValue = "1") Integer page,
                                                   @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        workspaceService.requireMember(user, workspaceId);
        return Result.ok(knowledgeDocService.listDocs(workspaceId, page, pageSize));
    }

    @GetMapping("/docs/{docId}")
    public Result<KnowledgeDocVO> detail(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                         @PathVariable Long docId) {
        workspaceService.requireMember(user, workspaceId);
        return Result.ok(knowledgeDocService.getDoc(docId, workspaceId));
    }

    @PatchMapping("/docs/{docId}/status")
    public Result<Void> archive(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                @PathVariable Long docId, @RequestBody ChangeStatusRequest request) {
        WorkspaceContext context = workspaceService.requireKnowledgeWrite(user, workspaceId);
        if (request != null && "ARCHIVED".equalsIgnoreCase(request.getStatus())) {
            knowledgeDocService.archiveDoc(docId, context.workspaceId(), context.userId());
        }
        return Result.ok();
    }

    @DeleteMapping("/docs/{docId}")
    public Result<Void> deleteCompat(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                     @PathVariable Long docId) {
        WorkspaceContext context = workspaceService.requireKnowledgeWrite(user, workspaceId);
        knowledgeDocService.archiveDoc(docId, context.workspaceId(), context.userId());
        return Result.ok();
    }
}
