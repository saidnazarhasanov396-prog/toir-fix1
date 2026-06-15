package com.toir.controller;

import com.toir.dto.notificationdevice.NotificationDeviceDto;
import com.toir.dto.notificationdevice.NotificationDeviceRegisterRequest;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.NotificationDeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-devices")
@Tag(name = "notification-devices")
@RequiredArgsConstructor
public class NotificationDeviceController {

    private final NotificationDeviceService service;

    @PostMapping("/register")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationDeviceDto> register(@CurrentUser AuthenticatedUser user,
                                                          @Valid @RequestBody NotificationDeviceRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(currentUserId(user), request));
    }

    @DeleteMapping("/{token:.+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unregister(@CurrentUser AuthenticatedUser user, @PathVariable String token) {
        service.unregister(currentUserId(user), token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationDeviceDto>> mine(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(service.findMine(currentUserId(user)));
    }

    private UUID currentUserId(AuthenticatedUser user) {
        return user != null ? UUID.fromString(user.id()) : null;
    }
}
