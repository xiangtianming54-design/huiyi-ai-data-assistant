package com.huiyi.assistant.context;

public record WorkspaceContext(Long userId, Long workspaceId, Long membershipId, String roleCode,
                               String memberStatus, String userStatus) {

    public boolean canManageMembers() {
        return "OWNER".equals(roleCode) || "ADMIN".equals(roleCode);
    }

    public boolean canUploadKnowledge() {
        return !"VIEWER".equals(roleCode);
    }
}
