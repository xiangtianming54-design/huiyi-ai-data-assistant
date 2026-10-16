package com.huiyi.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "huiyi.auth")
public class AuthProperties {

    private boolean registerEnabled = true;
    private String defaultMemberRole = "VIEWER";
    private String defaultWorkspaceCode = "default";
    private String jwtSecret = "";
    private String jwtIssuer = "huiyi";
    private String jwtAudience = "huiyi-api";
    private long accessTokenTtlSeconds = 1800;
    private long refreshTokenTtlSeconds = 604800;
    private int loginFailMaxAttempts = 5;
    private long loginFailWindowSeconds = 900;
    private long loginLockSeconds = 900;
}
