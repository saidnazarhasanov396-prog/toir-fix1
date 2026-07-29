package com.toir.service.maintanance;

import com.toir.enums.MaintenanceScheduleContentHashVersion;
import com.toir.exception.MaintenanceScheduleCalculationConflictException;
import com.toir.exception.MaintenanceScheduleCalculationConflictException.Reason;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MaintenanceScheduleContentHasher {

    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final int FINGERPRINT_LENGTH = 12;

    private final MaintenanceScheduleCanonicalContentSerializerV1 serializerV1;

    public String compute(
            int version,
            MaintenanceScheduleCalculationContent content) {
        return compute(
                MaintenanceScheduleContentHashVersion.fromPersistedValue(version),
                content
        );
    }

    public String compute(
            MaintenanceScheduleContentHashVersion version,
            MaintenanceScheduleCalculationContent content) {
        return HexFormat.of().formatHex(digest(version, content));
    }

    public void verify(
            int version,
            MaintenanceScheduleCalculationContent content,
            String expectedHash) {
        MaintenanceScheduleContentHashVersion resolved =
                MaintenanceScheduleContentHashVersion.fromPersistedValue(version);
        requireValidHash(expectedHash);
        byte[] expected = HexFormat.of().parseHex(expectedHash);
        byte[] calculated = digest(resolved, content);
        if (!MessageDigest.isEqual(expected, calculated)) {
            throw new MaintenanceScheduleCalculationConflictException(
                    Reason.HASH_MISMATCH);
        }
    }

    public String fingerprint(String hash) {
        requireValidHash(hash);
        return hash.substring(0, FINGERPRINT_LENGTH);
    }

    private byte[] digest(
            MaintenanceScheduleContentHashVersion version,
            MaintenanceScheduleCalculationContent content) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(content, "content");
        String canonical = serializer(version).serialize(content);
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private MaintenanceScheduleCanonicalContentSerializer serializer(
            MaintenanceScheduleContentHashVersion version) {
        return switch (version) {
            case V1 -> serializerV1;
        };
    }

    private static void requireValidHash(String hash) {
        if (hash == null || !HASH_PATTERN.matcher(hash).matches()) {
            throw new MaintenanceScheduleCalculationConflictException(
                    Reason.INVALID_HASH);
        }
    }
}
