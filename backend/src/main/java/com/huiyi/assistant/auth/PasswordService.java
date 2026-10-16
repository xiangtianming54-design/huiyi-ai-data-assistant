package com.huiyi.assistant.auth;

import com.huiyi.assistant.common.exception.BizException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PasswordService {

    private static final int MIN_LENGTH = 10;
    private static final Set<String> WEAK = Set.of(
            "password123", "1234567890", "qwerty1234", "admin12345",
            "letmein123", "password12", "1111111111", "0000000000");

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public void validate(String raw) {
        if (raw == null || raw.length() < MIN_LENGTH) {
            throw new BizException(400, "密码至少 10 位");
        }
        if (WEAK.contains(raw.toLowerCase())) {
            throw new BizException(400, "密码过于简单");
        }
    }

    public String hash(String raw) {
        return encoder.encode(raw);
    }

    public boolean matches(String raw, String hash) {
        if (raw == null || hash == null || !hash.startsWith("$2")) {
            return false;
        }
        return encoder.matches(raw, hash);
    }
}
