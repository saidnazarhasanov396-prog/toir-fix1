package com.toir.service.file_management;

import com.toir.exception.RestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class LocalFileResourceResolver {

    private final Path storageRoot;

    public LocalFileResourceResolver(@Value("${app.files.storage-path:uploads}") String storagePath) {
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    public FileSystemResource load(String storedPath) {
        Path resolved = resolve(storedPath);
        if (!Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
            throw RestException.notFound("File not found");
        }
        return new FileSystemResource(resolved);
    }

    public Path resolve(String storedPath) {
        if (!StringUtils.hasText(storedPath)) {
            throw RestException.notFound("File not found");
        }
        Path resolved = toPath(storedPath.trim()).normalize();
        if (!resolved.isAbsolute()) {
            resolved = resolveRelative(resolved).normalize();
        }
        if (!resolved.startsWith(storageRoot)) {
            throw RestException.notFound("File not found");
        }
        return resolved;
    }

    public static boolean looksLikeLocalReference(String storedPath) {
        if (!StringUtils.hasText(storedPath)) {
            return false;
        }
        String value = storedPath.trim();
        return value.startsWith("file:")
                || value.startsWith("/")
                || value.startsWith("\\")
                || value.startsWith("./")
                || value.startsWith(".\\")
                || value.startsWith("uploads/")
                || value.startsWith("uploads\\");
    }

    private Path toPath(String storedPath) {
        if (storedPath.startsWith("file:")) {
            return pathFromFileUri(storedPath);
        }
        return Paths.get(UriUtils.decode(storedPath, StandardCharsets.UTF_8));
    }

    private Path pathFromFileUri(String storedPath) {
        try {
            return Paths.get(new URI(storedPath));
        } catch (IllegalArgumentException | URISyntaxException e) {
            String withoutScheme = storedPath.replaceFirst("^file:(//)?", "");
            if (!withoutScheme.startsWith("/") && !withoutScheme.startsWith("\\")) {
                withoutScheme = "/" + withoutScheme;
            }
            return Paths.get(UriUtils.decode(withoutScheme, StandardCharsets.UTF_8));
        }
    }

    private Path resolveRelative(Path relativePath) {
        Path fileName = storageRoot.getFileName();
        if (fileName != null && relativePath.getNameCount() > 0 && fileName.equals(relativePath.getName(0))) {
            Path parent = storageRoot.getParent();
            if (parent != null) {
                return parent.resolve(relativePath);
            }
        }
        return storageRoot.resolve(relativePath);
    }
}
