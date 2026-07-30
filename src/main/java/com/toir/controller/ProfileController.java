package com.toir.controller;

import com.toir.dto.profile.ChangePasswordRequest;
import com.toir.dto.profile.ChangePasswordResponse;
import com.toir.dto.profile.ProfileResponse;
import com.toir.dto.profile.UpdateProfileRequest;
import com.toir.security.AuthenticatedUser;
import com.toir.security.CurrentUser;
import com.toir.service.users.ProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(profileService.getProfile(currentUserId(user)));
    }

    @PatchMapping
    public ResponseEntity<ProfileResponse> updateProfile(
            @CurrentUser AuthenticatedUser user,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(profileService.updateProfile(currentUserId(user), request));
    }

    @PutMapping(value = "/avatar", consumes = "multipart/form-data")
    public ResponseEntity<ProfileResponse> uploadAvatar(
            @CurrentUser AuthenticatedUser user,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(profileService.uploadAvatar(currentUserId(user), file));
    }

    @GetMapping("/avatar")
    public ResponseEntity<Resource> downloadAvatar(@CurrentUser AuthenticatedUser user) {
        ProfileService.AvatarDownload avatar = profileService.downloadAvatar(currentUserId(user));
        return ResponseEntity.ok()
                .contentType(avatar.contentType())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(avatar.originalName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(avatar.resource());
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<Void> deleteAvatar(@CurrentUser AuthenticatedUser user) {
        profileService.deleteAvatar(currentUserId(user));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<ChangePasswordResponse> changePassword(
            @CurrentUser AuthenticatedUser user,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return ResponseEntity.ok(profileService.changePassword(currentUserId(user), request));
    }

    private UUID currentUserId(AuthenticatedUser user) {
        if (user == null || user.id() == null) {
            throw com.toir.exception.RestException.unauthorized("Authenticated user is required");
        }
        try {
            return UUID.fromString(user.id());
        } catch (IllegalArgumentException exception) {
            throw com.toir.exception.RestException.unauthorized("Authenticated user is unavailable");
        }
    }
}
