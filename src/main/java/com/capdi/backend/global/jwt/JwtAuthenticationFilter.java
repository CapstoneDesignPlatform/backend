package com.capdi.backend.global.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String REFRESH_PATH = "/auth/refresh";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            boolean isRefreshPath = REFRESH_PATH.equals(request.getServletPath());

            if (isRefreshPath) {
                setAuthIgnoringExpiry(token);
            } else if (jwtUtil.validateToken(token)) {
                setAuth(token);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void setAuth(String token) {
        Long userId = jwtUtil.getUserId(token);
        String role = jwtUtil.getRole(token);
        authenticate(userId, role);
    }

    private void setAuthIgnoringExpiry(String token) {
        try {
            Long userId = jwtUtil.getUserIdIgnoringExpiry(token);
            String role = jwtUtil.getRoleIgnoringExpiry(token);
            authenticate(userId, role);
        } catch (Exception e) {
            log.warn("Failed to parse JWT for refresh: {}", e.getClass().getSimpleName());
        }
    }

    private void authenticate(Long userId, String role) {
        CustomUserDetails userDetails = new CustomUserDetails(userId, role);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
