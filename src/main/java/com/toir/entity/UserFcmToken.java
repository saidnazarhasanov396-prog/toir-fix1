package com.toir.entity;

import com.toir.audit.AuditedResource;
import com.toir.entity.users.User;
import com.toir.enums.AuditModule;
import com.toir.enums.FcmDevicePlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_fcm_tokens")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@AuditedResource(
        module = AuditModule.USER,
        entityType = "user_fcm_tokens",
        ignoredFields = {"lastSeenAt"}
)
public class UserFcmToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "text")
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FcmDevicePlatform platform;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "language_code", nullable = false, length = 2)
    private String languageCode = "ru";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
