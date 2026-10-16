package com.huiyi.assistant.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.assistant.mapper.AuditEventMapper;
import com.huiyi.assistant.model.entity.AuditEvent;
import com.huiyi.assistant.observability.ObservabilityContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class AuditService {

    private static final Set<String> SECRET_KEYS = Set.of(
            "password", "oldpassword", "newpassword", "accesstoken", "refreshtoken", "token");

    private final AuditEventMapper auditEventMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuditService(AuditEventMapper auditEventMapper) {
        this.auditEventMapper = auditEventMapper;
    }

    public void accessDenied(Long userId, String username, Long workspaceId, String reason) {
        record(AuditAction.ACCESS_DENIED, "DENIED", userId, username, workspaceId,
                "workspace", workspaceId == null ? null : String.valueOf(workspaceId),
                reason == null ? Map.of() : Map.of("reason", reason));
    }

    public void record(String action, String resultStatus, Long userId, String username, Long workspaceId,
                       String resourceType, String resourceId, Map<String, ?> detail) {
        AuditEvent event = new AuditEvent();
        event.setAction(action);
        event.setResultStatus(resultStatus);
        event.setUserId(userId);
        event.setUsername(username);
        event.setWorkspaceId(workspaceId);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId);
        event.setDetailJson(toJson(sanitize(detail)));
        event.setRequestId(ObservabilityContextHolder.requestId());
        event.setCreatedAt(LocalDateTime.now());
        HttpServletRequest request = currentRequest();
        if (request != null) {
            event.setIp(clientIp(request));
            event.setUserAgent(trim(request.getHeader("User-Agent"), 255));
        }
        try {
            auditEventMapper.insert(event);
        } catch (RuntimeException e) {
            log.error("审计写入失败 action={} user={} workspace={}", action, username, workspaceId, e);
        }
    }

    private Map<String, Object> sanitize(Map<String, ?> detail) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (detail == null) {
            return clean;
        }
        for (Map.Entry<String, ?> entry : detail.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().replace("_", "").toLowerCase(Locale.ROOT);
            if (SECRET_KEYS.contains(key)) {
                continue;
            }
            clean.put(entry.getKey(), entry.getValue());
        }
        return clean;
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return trim(forwarded.split(",")[0].trim(), 64);
        }
        return trim(request.getRemoteAddr(), 64);
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
