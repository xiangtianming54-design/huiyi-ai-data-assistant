package com.huiyi.assistant.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MeVO {

    private Long userId;
    private String username;
    private String displayName;
    private String status;
    private List<WorkspaceMembershipVO> workspaces;
}
