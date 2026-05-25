package com.toir.service.file_management;

import com.toir.enums.ErrorType;
import com.toir.exception.RestException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class FileValidator {

    public static final long MAX_FILE_SIZE = 150 * 1024 * 1024L;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "application/pdf",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "webp", "pdf", "txt", "doc", "docx", "xls", "xlsx"
    );

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "sh", "bat", "cmd", "js", "jar", "war", "php", "py", "dll"
    );

    private final Tika tika = new Tika();

    public ValidatedFile validate(MultipartFile file) {
        if (file == null) {
            throw RestException.restThrow(ErrorType.INVALID_FILE);
        }
        if (file.isEmpty()) {
            throw RestException.restThrow(ErrorType.FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw RestException.restThrow(ErrorType.FILE_TOO_LARGE);
        }

        String originalName = sanitizeFilename(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!StringUtils.hasText(extension)
                || !ALLOWED_EXTENSIONS.contains(extension)
                || hasBlockedExtension(originalName)) {
            throw RestException.restThrow(ErrorType.FILE_TYPE_NOT_ALLOWED);
        }

        String detectedMime = detectMime(file, originalName);
        if (!ALLOWED_MIME_TYPES.contains(detectedMime)) {
            throw RestException.restThrow(ErrorType.FILE_TYPE_NOT_ALLOWED);
        }

        return ValidatedFile.builder()
                .originalName(originalName)
                .extension(extension)
                .contentType(detectedMime)
                .size(file.getSize())
                .build();
    }

    private String sanitizeFilename(String filename) {
        String value = StringUtils.hasText(filename) ? filename : "file";
        value = value.replace("\\", "/");
        value = value.substring(value.lastIndexOf('/') + 1);
        value = Normalizer.normalize(value, Normalizer.Form.NFKC);
        value = value.replaceAll("[\\r\\n\\t]", "");
        value = value.replaceAll("[^A-Za-z0-9._ -]", "_");
        value = value.replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(value) || ".".equals(value) || "..".equals(value)) {
            throw RestException.restThrow(ErrorType.INVALID_FILE);
        }
        return value;
    }

    private String extensionOf(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private boolean hasBlockedExtension(String filename) {
        String[] parts = filename.toLowerCase(Locale.ROOT).split("\\.");
        for (int i = 1; i < parts.length; i++) {
            if (BLOCKED_EXTENSIONS.contains(parts[i])) {
                return true;
            }
        }
        return false;
    }

    private String detectMime(MultipartFile file, String filename) {
        try (InputStream inputStream = file.getInputStream()) {
            String detected = tika.detect(inputStream, filename);
            return StringUtils.hasText(detected) ? detected : "application/octet-stream";
        } catch (Exception e) {
            throw RestException.restThrow(ErrorType.INVALID_FILE);
        }
    }

    @Builder
    public record ValidatedFile(
            String originalName,
            String extension,
            String contentType,
            Long size
    ) {
    }
}
