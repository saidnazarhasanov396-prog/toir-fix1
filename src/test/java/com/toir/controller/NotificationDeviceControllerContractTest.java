package com.toir.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toir.dto.notificationdevice.NotificationDeviceDto;
import com.toir.dto.notificationdevice.NotificationDeviceRegisterRequest;
import com.toir.enums.FcmDevicePlatform;
import com.toir.exception.GlobalExceptionHandler;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUserArgumentResolver;
import com.toir.service.NotificationDeviceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationDeviceControllerContractTest {

    @Mock
    NotificationDeviceService service;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationDeviceController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerPassesCurrentUserAndReturnsCreatedDevice() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        authenticate(userId);
        NotificationDeviceRegisterRequest request = new NotificationDeviceRegisterRequest(
                "fcm-token",
                FcmDevicePlatform.ANDROID,
                "phone-1"
        );
        when(service.register(eq(userId), eq(request))).thenReturn(new NotificationDeviceDto(
                deviceId,
                userId,
                "fcm-token",
                FcmDevicePlatform.ANDROID,
                "phone-1",
                true,
                Instant.parse("2026-06-15T05:00:00Z"),
                Instant.parse("2026-06-15T05:00:00Z"),
                Instant.parse("2026-06-15T05:00:00Z")
        ));

        mockMvc.perform(post("/api/v1/notification-devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.platform").value(FcmDevicePlatform.ANDROID.name()))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void unregisterPassesCurrentUserAndToken() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId);

        mockMvc.perform(delete("/api/v1/notification-devices/{token}", "fcm-token"))
                .andExpect(status().isNoContent());

        verify(service).unregister(userId, "fcm-token");
    }

    @Test
    void meReturnsCurrentUsersDevices() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticate(userId);
        when(service.findMine(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/notification-devices/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    private void authenticate(UUID userId) {
        AuthenticatedUser user = new AuthenticatedUser(
                userId.toString(),
                "user",
                "user@example.test",
                "User",
                UUID.randomUUID().toString(),
                "MAINTENANCE_FOREMAN",
                List.of("NOTIFICATION_READ")
        );
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                user,
                "n/a",
                "NOTIFICATION_READ"
        ));
    }
}
