package com.toir.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.exception.ErrorResponse;
import com.toir.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityErrorHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void authenticationEntryPointReturnsGeneric401WithoutExceptionDetails() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationEntryPoint(objectMapper).commence(
                request,
                response,
                new BadCredentialsException("sensitive authentication detail")
        );

        ErrorResponse body = objectMapper.readValue(response.getContentAsByteArray(), ErrorResponse.class);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.message()).isEqualTo("Unauthorized");
        assertThat(body.path()).isEqualTo("/api/v1/protected");
        assertThat(response.getContentAsString()).doesNotContain("sensitive authentication detail");
    }

    @Test
    void accessDeniedHandlerReturnsGeneric403WithoutExceptionDetails() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAccessDeniedHandler(objectMapper).handle(
                request,
                response,
                new AccessDeniedException("sensitive authorization detail")
        );

        ErrorResponse body = objectMapper.readValue(response.getContentAsByteArray(), ErrorResponse.class);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.message()).isEqualTo("Access denied");
        assertThat(body.path()).isEqualTo("/api/v1/protected");
        assertThat(response.getContentAsString()).doesNotContain("sensitive authorization detail");
    }

    @Test
    void controllerAdviceMasksAccessDeniedExceptionMessage() {
        var response = new GlobalExceptionHandler().handleAccessDenied(
                new AccessDeniedException("sensitive scope detail"),
                request()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Access denied");
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/v1/protected");
    }
}
