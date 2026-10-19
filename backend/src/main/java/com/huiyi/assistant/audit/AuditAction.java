package com.huiyi.assistant.audit;

public final class AuditAction {

    public static final String AUTH_REGISTER = "AUTH_REGISTER";
    public static final String AUTH_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
    public static final String AUTH_LOGIN_FAILED = "AUTH_LOGIN_FAILED";
    public static final String AUTH_REFRESH = "AUTH_REFRESH";
    public static final String AUTH_LOGOUT = "AUTH_LOGOUT";
    public static final String USER_STATUS_CHANGED = "USER_STATUS_CHANGED";
    public static final String WORKSPACE_CREATED = "WORKSPACE_CREATED";
    public static final String MEMBER_ADDED = "MEMBER_ADDED";
    public static final String MEMBER_ROLE_CHANGED = "MEMBER_ROLE_CHANGED";
    public static final String MEMBER_STATUS_CHANGED = "MEMBER_STATUS_CHANGED";
    public static final String KNOWLEDGE_UPLOADED = "KNOWLEDGE_UPLOADED";
    public static final String KNOWLEDGE_ARCHIVED = "KNOWLEDGE_ARCHIVED";
    public static final String KNOWLEDGE_SPACE_CREATED = "KNOWLEDGE_SPACE_CREATED";
    public static final String KNOWLEDGE_SPACE_UPDATED = "KNOWLEDGE_SPACE_UPDATED";
    public static final String KNOWLEDGE_SPACE_ARCHIVED = "KNOWLEDGE_SPACE_ARCHIVED";
    public static final String CHAT_ASKED = "CHAT_ASKED";
    public static final String ANALYSIS_QUERIED = "ANALYSIS_QUERIED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String RATE_LIMITED = "RATE_LIMITED";

    private AuditAction() {
    }
}
