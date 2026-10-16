package com.huiyi.assistant.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkspaceMembershipVO {

    private Long workspaceId;
    private String code;
    private String name;
    private String role;
    private String memberStatus;
}
