package com.toir.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterWebSocketTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsAccessTokenQueryOnlyForMeterWebSocketHandshake() throws Exception {
        Claims validClaims = claims();
        when(jwtService.parse("valid-token")).thenReturn(validClaims);
        MockHttpServletRequest handshake = new MockHttpServletRequest("GET", "/api/v1/ws/meters");
        handshake.setParameter("access_token", "valid-token");
        handshake.addHeader("Upgrade", "websocket");

        filter.doFilter(handshake, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

        SecurityContextHolder.clearContext();
        MockHttpServletRequest ordinaryRequest = new MockHttpServletRequest("GET", "/api/v1/meters");
        ordinaryRequest.setParameter("access_token", "valid-token");

        filter.doFilter(ordinaryRequest, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void bearerHeaderTakesPrecedenceOverMeterWebSocketQueryToken() throws Exception {
        Claims validClaims = claims();
        when(jwtService.parse("header-token")).thenReturn(validClaims);
        MockHttpServletRequest handshake = new MockHttpServletRequest("GET", "/api/v1/ws/meters");
        handshake.addHeader("Authorization", "Bearer header-token");
        handshake.setParameter("access_token", "query-token");
        handshake.addHeader("Upgrade", "websocket");

        filter.doFilter(handshake, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        org.mockito.Mockito.verify(jwtService).parse("header-token");
        org.mockito.Mockito.verify(jwtService, org.mockito.Mockito.never()).parse("query-token");
    }

    private Claims claims() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user-id");
        when(claims.get("username", String.class)).thenReturn("user");
        when(claims.get("email", String.class)).thenReturn("user@example.com");
        when(claims.get("fullName", String.class)).thenReturn("User");
        when(claims.get("departmentId", String.class)).thenReturn(null);
        when(claims.get("primaryRoleCode", String.class)).thenReturn("VIEWER");
        when(claims.get("authorities")).thenReturn(List.of("VIEWER"));
        when(claims.get("permissions")).thenReturn(List.of());
        return claims;
    }
}
