package com.huiyi.assistant.security;

import com.huiyi.assistant.auth.AuthService;
import com.huiyi.assistant.auth.JwtTokenService;
import com.huiyi.assistant.mapper.UserMapper;
import com.huiyi.assistant.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, UserMapper userMapper) {
        this.jwtTokenService = jwtTokenService;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtTokenService.parseAccess(token);
                Long userId = Long.valueOf(claims.getSubject());
                User user = userMapper.selectById(userId);
                if (user != null && AuthService.STATUS_ACTIVE.equals(user.getStatus())
                        && !jwtTokenService.isTokenVersionMismatch(token, user)) {
                    AuthUser principal = new AuthUser(user.getId(), user.getUsername(),
                            user.getTokenVersion() == null ? 0 : user.getTokenVersion());
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
