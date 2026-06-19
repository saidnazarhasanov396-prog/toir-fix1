package com.toir.service.file_management;

import com.toir.exception.RestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileValidatorTest {

    private final FileValidator validator = new FileValidator();

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(RestException.class)
                .hasMessage("File is empty");
    }

    @Test
    void rejectsOversizedFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(FileValidator.MAX_FILE_SIZE + 1);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(RestException.class)
                .hasMessage("File size exceeds the allowed limit");
    }

    @Test
    void rejectsBlockedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "run.exe", "application/octet-stream", "MZ".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(RestException.class)
                .hasMessage("File type is not allowed");
    }

    @Test
    void rejectsUnsupportedMimeType() {
        MockMultipartFile file = new MockMultipartFile("file", "archive.txt", "application/octet-stream",
                new byte[]{0x7F, 0x45, 0x4C, 0x46});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(RestException.class)
                .hasMessage("File type is not allowed");
    }

    @Test
    void acceptsValidPdfImageAndDocLikeFile() {
        MockMultipartFile pdf = new MockMultipartFile("file", "report.pdf", "application/pdf", "%PDF-1.4\n".getBytes());
        MockMultipartFile png = new MockMultipartFile("file", "image.png", "image/png", pngBytes());
        MockMultipartFile doc = new MockMultipartFile("file", "notes.doc", "application/msword", "plain document text".getBytes());

        assertThat(validator.validate(pdf).contentType()).isEqualTo("application/pdf");
        assertThat(validator.validate(png).contentType()).isEqualTo("image/png");
        assertThat(validator.validate(doc).contentType()).isEqualTo("text/plain");
    }

    @Test
    void acceptsValidGifImage() {
        MockMultipartFile gif = new MockMultipartFile("file", "image.gif", "image/gif", gifBytes());

        assertThat(validator.validate(gif).contentType()).isEqualTo("image/gif");
    }

    private byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x02, 0x00, 0x00, 0x00
        };
    }

    private byte[] gifBytes() {
        return new byte[]{
                0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
                0x01, 0x00, 0x01, 0x00,
                (byte) 0x80, 0x00, 0x00,
                0x00, 0x00, 0x00,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                0x2C, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x00, 0x01, 0x00,
                0x00, 0x02, 0x02, 0x44, 0x01,
                0x00, 0x3B
        };
    }
}
