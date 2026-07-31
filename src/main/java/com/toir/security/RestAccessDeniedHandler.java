package com.toir.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.exception.ErrorResponse;
import com.toir.exception.BackendErrorLocalizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        BackendErrorLocalizer.LocalizedError localized = BackendErrorLocalizer.localize(
                request, HttpStatus.FORBIDDEN, "ACCESS_DENIED", Map.of(), null, false);
        response.setHeader(org.springframework.http.HttpHeaders.CONTENT_LANGUAGE,
                BackendErrorLocalizer.resolveLocale(request.getHeader(
                        org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE)).getLanguage());
        objectMapper.writeValue(
                response.getOutputStream(),
                ErrorResponse.of(localized.message(), request.getRequestURI(), HttpStatus.FORBIDDEN.value(),
                        localized.errorCode(), localized.params())
        );
    }
}
