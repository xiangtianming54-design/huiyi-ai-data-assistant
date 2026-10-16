package com.huiyi.assistant.auth;

import com.huiyi.assistant.config.AuthProperties;
import com.huiyi.assistant.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {

    private final AuthProperties properties;

    public JwtTokenService(AuthProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("typ", "access")
                .claim("tv", user.getTokenVersion() == null ? 0 : user.getTokenVersion())
                .issuer(properties.getJwtIssuer())
                .audience().add(properties.getJwtAudience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getAccessTokenTtlSeconds())))
                .signWith(signingKey())
                .compact();
    }

    public Claims parseAccess(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(properties.getJwtIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenVersionMismatch(String accessToken, User user) {
        Claims claims = parseAccess(accessToken);
        Number tv = claims.get("tv", Number.class);
        int expected = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        return tv == null || tv.intValue() != expected;
    }

    private SecretKey signingKey() {
        byte[] bytes = properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足 32 字节");
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
