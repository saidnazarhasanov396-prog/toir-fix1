package com.toir.service.users;

import com.toir.exception.RestException;
import com.toir.service.file_management.FileValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileAvatarValidatorTest {

    private final ProfileAvatarValidator validator = new ProfileAvatarValidator(new FileValidator());

    @Test
    void acceptsStructurallyValidPng() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        );

        FileValidator.ValidatedFile result = validator.validate(
                new MockMultipartFile("file", "avatar.png", "image/png", png)
        );

        assertThat(result.contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsSvgEvenWhenNamedAsPng() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes()
        );

        assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(RestException.class);
    }

    @Test
    void rejectsOversizedAvatarBeforeStorage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                new byte[(int) ProfileAvatarValidator.MAX_AVATAR_SIZE + 1]
        );

        assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(RestException.class);
    }
}
