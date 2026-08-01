package com.toir.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.exception.ErrorResponse;
import com.toir.exception.BackendErrorLocalizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        BackendErrorLocalizer.LocalizedError localized = BackendErrorLocalizer.localize(
                request, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", Map.of(), null, false);
        response.setHeader(org.springframework.http.HttpHeaders.CONTENT_LANGUAGE,
                BackendErrorLocalizer.resolveLocale(request.getHeader(
                        org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE)).getLanguage());
        objectMapper.writeValue(
                response.getOutputStream(),
                ErrorResponse.of(localized.message(), request.getRequestURI(), HttpStatus.UNAUTHORIZED.value(),
                        localized.errorCode(), localized.params())
        );
    }
}
