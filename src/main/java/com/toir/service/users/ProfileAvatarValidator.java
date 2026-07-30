package com.toir.service.users;

import com.toir.exception.RestException;
import com.toir.service.file_management.FileValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.imageio.ImageIO;

@Component
@RequiredArgsConstructor
public class ProfileAvatarValidator {

    public static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024L;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final FileValidator fileValidator;

    public FileValidator.ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw RestException.badRequest("Avatar file must not be empty", "PROFILE_AVATAR_EMPTY");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw RestException.badRequest(
                    "Avatar file must not exceed 5 MB",
                    "PROFILE_AVATAR_TOO_LARGE"
            );
        }

        FileValidator.ValidatedFile validated = fileValidator.validate(file);
        if (!ALLOWED_TYPES.contains(validated.contentType())) {
            throw RestException.badRequest(
                    "Avatar must be a JPEG, PNG, or WebP image",
                    "PROFILE_AVATAR_TYPE_NOT_ALLOWED"
            );
        }
        if (!extensionMatches(validated.contentType(), validated.extension())) {
            throw RestException.badRequest(
                    "Avatar filename extension does not match the image content",
                    "PROFILE_AVATAR_EXTENSION_MISMATCH"
            );
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw RestException.badRequest("Avatar image could not be read", "PROFILE_AVATAR_INVALID");
        }
        if (!hasValidStructure(validated.contentType(), bytes)) {
            throw RestException.badRequest("Avatar image is corrupted", "PROFILE_AVATAR_INVALID");
        }
        return validated;
    }

    private boolean hasValidStructure(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/jpeg" -> isJpeg(bytes) && isDecodable(bytes);
            case "image/png" -> isPng(bytes) && isDecodable(bytes);
            case "image/webp" -> isWebp(bytes);
            default -> false;
        };
    }

    private boolean extensionMatches(String contentType, String extension) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg".equals(extension) || "jpeg".equals(extension);
            case "image/png" -> "png".equals(extension);
            case "image/webp" -> "webp".equals(extension);
            default -> false;
        };
    }

    private boolean isDecodable(byte[] bytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(input) != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 4
                && unsigned(bytes[0]) == 0xff
                && unsigned(bytes[1]) == 0xd8
                && unsigned(bytes[bytes.length - 2]) == 0xff
                && unsigned(bytes[bytes.length - 1]) == 0xd9;
    }

    private boolean isPng(byte[] bytes) {
        byte[] signature = {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };
        if (bytes.length < 20) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) {
                return false;
            }
        }
        return bytes[bytes.length - 8] == 0x49
                && bytes[bytes.length - 7] == 0x45
                && bytes[bytes.length - 6] == 0x4e
                && bytes[bytes.length - 5] == 0x44;
    }

    private boolean isWebp(byte[] bytes) {
        if (bytes.length < 16) {
            return false;
        }
        String riff = new String(bytes, 0, 4, StandardCharsets.US_ASCII);
        String webp = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
        long declaredLength = Integer.toUnsignedLong(
                unsigned(bytes[4])
                        | (unsigned(bytes[5]) << 8)
                        | (unsigned(bytes[6]) << 16)
                        | (unsigned(bytes[7]) << 24)
        );
        return "RIFF".equals(riff) && "WEBP".equals(webp) && declaredLength + 8L <= bytes.length;
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }
}
