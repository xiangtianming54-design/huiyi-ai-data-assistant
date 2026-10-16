package com.huiyi.assistant.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthTokensVO {

    private Long userId;
    private String username;
    private String displayName;
    private String accessToken;
    private String refreshToken;
    private Long defaultWorkspaceId;
}
