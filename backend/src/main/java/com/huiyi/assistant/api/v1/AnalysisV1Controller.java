package com.huiyi.assistant.api.v1;

import com.huiyi.assistant.audit.AuditAction;
import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.context.WorkspaceContextHolder;
import com.huiyi.assistant.model.dto.AnalysisRequestDTO;
import com.huiyi.assistant.model.vo.AnalysisResultVO;
import com.huiyi.assistant.security.AuthUser;
import com.huiyi.assistant.service.AnalysisService;
import com.huiyi.assistant.workspace.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/analysis")
public class AnalysisV1Controller {

    private final WorkspaceService workspaceService;
    private final AnalysisService analysisService;
    private final AuditService auditService;

    public AnalysisV1Controller(WorkspaceService workspaceService, AnalysisService analysisService,
                                AuditService auditService) {
        this.workspaceService = workspaceService;
        this.analysisService = analysisService;
        this.auditService = auditService;
    }

    @PostMapping("/query")
    public Result<AnalysisResultVO> query(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                          @Valid @RequestBody AnalysisRequestDTO request) {
        var context = workspaceService.requireMember(user, workspaceId);
        auditService.record(AuditAction.ANALYSIS_QUERIED, "SUCCESS", user.userId(), user.username(), workspaceId,
                "analysis", request.getSessionId(),
                java.util.Map.of("question", request.getQuestion() == null ? "" : request.getQuestion()));
        WorkspaceContextHolder.set(context);
        try {
            return Result.ok(analysisService.query(request));
        } finally {
            WorkspaceContextHolder.clear();
        }
    }
}
