package com.huiyi.assistant.api.v1;

import com.huiyi.assistant.audit.AuditAction;
import com.huiyi.assistant.audit.AuditService;
import com.huiyi.assistant.common.result.Result;
import com.huiyi.assistant.context.WorkspaceContext;
import com.huiyi.assistant.context.WorkspaceContextHolder;
import com.huiyi.assistant.model.dto.ChangeStatusRequest;
import com.huiyi.assistant.model.dto.ChatRequestDTO;
import com.huiyi.assistant.model.entity.ChatMessage;
import com.huiyi.assistant.model.entity.ChatSession;
import com.huiyi.assistant.model.vo.ChatAskResponseVO;
import com.huiyi.assistant.model.vo.ChatResponseVO;
import com.huiyi.assistant.model.vo.IntentResponseVO;
import com.huiyi.assistant.model.vo.PageResult;
import com.huiyi.assistant.security.AuthUser;
import com.huiyi.assistant.service.ChatService;
import com.huiyi.assistant.service.ChatSessionService;
import com.huiyi.assistant.workspace.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
public class ChatV1Controller {

    private final WorkspaceService workspaceService;
    private final ChatService chatService;
    private final ChatSessionService chatSessionService;
    private final AuditService auditService;

    public ChatV1Controller(WorkspaceService workspaceService, ChatService chatService,
                            ChatSessionService chatSessionService, AuditService auditService) {
        this.workspaceService = workspaceService;
        this.chatService = chatService;
        this.chatSessionService = chatSessionService;
        this.auditService = auditService;
    }

    @PostMapping("/chat/sessions")
    public Result<ChatSession> createSession(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId) {
        WorkspaceContext context = workspaceService.requireMember(user, workspaceId);
        return Result.ok(chatSessionService.createSession(context.workspaceId(), context.userId()));
    }

    @GetMapping("/chat/sessions")
    public Result<PageResult<ChatSession>> listSessions(@AuthenticationPrincipal AuthUser user,
                                                        @PathVariable Long workspaceId,
                                                        @RequestParam(required = false, defaultValue = "1") Integer page,
                                                        @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        WorkspaceContext context = workspaceService.requireMember(user, workspaceId);
        return Result.ok(PageResult.of(chatSessionService.listMine(context.workspaceId(), context.userId()),
                page, pageSize));
    }

    @GetMapping("/chat/sessions/{sessionId}")
    public Result<ChatSession> session(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                       @PathVariable String sessionId) {
        WorkspaceContext context = workspaceService.requireMember(user, workspaceId);
        return Result.ok(chatSessionService.requireOwned(context.workspaceId(), context.userId(), sessionId));
    }

    @GetMapping("/chat/sessions/{sessionId}/messages")
    public Result<List<ChatMessage>> messages(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                              @PathVariable String sessionId) {
        WorkspaceContext context = workspaceService.requireMember(user, workspaceId);
        chatSessionService.requireOwned(context.workspaceId(), context.userId(), sessionId);
        return Result.ok(chatSessionService.getRecentMessages(sessionId, 200));
    }

    @PatchMapping("/chat/sessions/{sessionId}/status")
    public Result<Void> archive(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                @PathVariable String sessionId, @RequestBody ChangeStatusRequest request) {
        WorkspaceContext context = workspaceService.requireMember(user, workspaceId);
        if (request != null && "ARCHIVED".equalsIgnoreCase(request.getStatus())) {
            chatSessionService.archive(context.workspaceId(), context.userId(), sessionId);
        }
        return Result.ok();
    }

    @PostMapping("/chat")
    public Result<ChatResponseVO> chat(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                       @Valid @RequestBody ChatRequestDTO request) {
        auditAsked(user, workspaceId, request);
        return withContext(user, workspaceId, () -> Result.ok(chatService.chat(request)));
    }

    @PostMapping("/chat/intent")
    public Result<IntentResponseVO> intent(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                           @Valid @RequestBody ChatRequestDTO request) {
        workspaceService.requireMember(user, workspaceId);
        return Result.ok(chatService.precheck(request.getQuestion()));
    }

    @PostMapping("/chat/ask")
    public Result<ChatAskResponseVO> ask(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                                         @Valid @RequestBody ChatRequestDTO request) {
        auditAsked(user, workspaceId, request);
        return withContext(user, workspaceId, () -> Result.ok(chatService.ask(request)));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthUser user, @PathVariable Long workspaceId,
                             @Valid @RequestBody ChatRequestDTO request) {
        WorkspaceContext context = workspaceService.requireMember(user, workspaceId);
        auditAsked(user, workspaceId, request);
        WorkspaceContextHolder.set(context);
        try {
            return chatService.chatStream(request);
        } finally {
            // SSE subscribe is async; keep context until stream method has bound the session.
            WorkspaceContextHolder.clear();
        }
    }

    private <T> T withContext(AuthUser user, Long workspaceId, java.util.function.Supplier<T> action) {
        WorkspaceContext context = workspaceService.requireMember(user, workspaceId);
        WorkspaceContextHolder.set(context);
        try {
            return action.get();
        } finally {
            WorkspaceContextHolder.clear();
        }
    }

    private void auditAsked(AuthUser user, Long workspaceId, ChatRequestDTO request) {
        auditService.record(AuditAction.CHAT_ASKED, "SUCCESS", user.userId(), user.username(), workspaceId,
                "chat", request.getSessionId(),
                java.util.Map.of("question", request.getQuestion() == null ? "" : request.getQuestion()));
    }
}
