package com.huiyi.assistant.security;

public record AuthUser(Long userId, String username, int tokenVersion) {
}
