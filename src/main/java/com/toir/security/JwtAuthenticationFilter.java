package com.toir.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(token);
                AuthenticatedUser principal = new AuthenticatedUser(
                        claims.getSubject(),
                        claims.get("username", String.class),
                        claims.get("email", String.class),
                        claims.get("fullName", String.class),
                        claims.get("departmentId", String.class),
                        claims.get("primaryRoleCode", String.class),
                        readList(claims, "permissions")
                );
                Set<String> authorityCodes = new LinkedHashSet<>(readList(claims, "authorities"));
                if (StringUtils.hasText(principal.primaryRoleCode())) {
                    authorityCodes.add(principal.primaryRoleCode());
                }
                expandKnownRolePermissions(authorityCodes);
                authorityCodes.addAll(principal.permissions());
                List<SimpleGrantedAuthority> authorities = authorityCodes.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        principal, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("Authenticated JWT subject={} username={} authorities={}",
                        claims.getSubject(), principal.username(), authorityCodes);
            } catch (JwtException ex) {
                SecurityContextHolder.clearContext();
                log.debug("Rejected JWT during authentication: {}", ex.getClass().getSimpleName());
            }
        }
        chain.doFilter(request, response);
    }

    private void expandKnownRolePermissions(Set<String> authorityCodes) {
        List<String> roleCodes = authorityCodes.stream()
                .filter(RolePermissionDefaults::hasDefaults)
                .toList();
        roleCodes.forEach(roleCode -> authorityCodes.addAll(RolePermissionDefaults.forRole(roleCode)));
    }

    private List<String> readList(Claims claims, String key) {
        Object value = claims.get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return Collections.emptyList();
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
