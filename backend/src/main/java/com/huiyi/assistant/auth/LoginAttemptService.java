package com.huiyi.assistant.auth;

import com.huiyi.assistant.config.AuthProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LoginAttemptService {

    private final StringRedisTemplate redis;
    private final AuthProperties properties;

    public LoginAttemptService(StringRedisTemplate redis, AuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public boolean isLocked(String username) {
        return Boolean.TRUE.equals(redis.hasKey(lockKey(username)));
    }

    public void recordFailure(String username) {
        String failKey = failKey(username);
        Long count = redis.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redis.expire(failKey, Duration.ofSeconds(properties.getLoginFailWindowSeconds()));
        }
        if (count != null && count >= properties.getLoginFailMaxAttempts()) {
            redis.opsForValue().set(lockKey(username), "1",
                    Duration.ofSeconds(properties.getLoginLockSeconds()));
        }
    }

    public void clear(String username) {
        redis.delete(failKey(username));
        redis.delete(lockKey(username));
    }

    private String failKey(String username) {
        return "login:fail:" + username;
    }

    private String lockKey(String username) {
        return "login:lock:" + username;
    }
}
