package com.huiyi.assistant.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MemberVO {

    private Long memberId;
    private Long userId;
    private String username;
    private String displayName;
    private String userStatus;
    private String memberStatus;
    private String role;
    private LocalDateTime joinedAt;
}
