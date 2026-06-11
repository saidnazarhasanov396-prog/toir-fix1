package com.toir.service.file_management;

import com.toir.exception.RestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileResourceResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesFileUrlWithDotSegmentAndEncodedSpaces() throws Exception {
        Path storageRoot = tempDir.resolve("uploads");
        Files.createDirectories(storageRoot);
        Path file = storageRoot.resolve("Screenshot 2026.png");
        Files.writeString(file, "png", StandardCharsets.UTF_8);
        String legacyUrl = storageRoot.resolve(".").resolve("Screenshot 2026.png")
                .toUri()
                .toString()
                .replace("Screenshot%202026.png", "Screenshot%202026.png");

        var resource = new LocalFileResourceResolver(storageRoot.toString()).load(legacyUrl);

        assertThat(resource.contentLength()).isEqualTo(3);
        assertThat(resource.getFile().toPath()).isEqualTo(file);
    }

    @Test
    void resolvesRelativeUploadsPathAgainstConfiguredRoot() throws Exception {
        Path storageRoot = tempDir.resolve("uploads");
        Files.createDirectories(storageRoot);
        Path file = storageRoot.resolve("report with spaces.pdf");
        Files.writeString(file, "pdf", StandardCharsets.UTF_8);

        var resource = new LocalFileResourceResolver(storageRoot.toString())
                .load("uploads/report%20with%20spaces.pdf");

        assertThat(resource.getFile().toPath()).isEqualTo(file);
    }

    @Test
    void missingPhysicalFileThrowsNotFound() {
        Path storageRoot = tempDir.resolve("uploads");

        assertThatThrownBy(() -> new LocalFileResourceResolver(storageRoot.toString()).load("uploads/missing.pdf"))
                .isInstanceOf(RestException.class)
                .hasMessage("File not found");
    }
}
