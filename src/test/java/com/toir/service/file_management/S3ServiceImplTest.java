package com.toir.service.file_management;

import com.toir.config.MinioProperties;
import com.toir.enums.ErrorType;
import com.toir.exception.RestException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3ServiceImplTest {

    private MinioClient minioClient;
    private S3ServiceImpl service;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioClient.class);
        MinioProperties properties = new MinioProperties();
        properties.setBucketName("bucket");
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("access");
        properties.setSecretKey("secret");
        service = new S3ServiceImpl(minioClient, properties);
    }

    @Test
    void uploadsObjectSuccessfully() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        service.store(file, "documents/2026/05/a.txt");

        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void deletesObjectSuccessfully() throws Exception {
        service.delete("documents/2026/05/a.txt");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void generatesPresignedUrl() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://signed-url");

        String result = service.generatePresignedUrl("documents/2026/05/a.txt", 15);

        assertThat(result).isEqualTo("http://signed-url");
    }

    @Test
    void handlesMinioException() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.store(file, "documents/2026/05/a.txt"))
                .isInstanceOf(RestException.class)
                .hasMessage("File upload failed");
    }

    @Test
    void mapsAccessDeniedStorageErrorToClearConfigurationMessage() {
        ErrorType result = S3ServiceImpl.mapStorageErrorCode("AccessDenied", ErrorType.FILE_UPLOAD_FAILED);

        assertThat(result).isEqualTo(ErrorType.FILE_STORAGE_ACCESS_DENIED);
    }

    @Test
    void mapsMissingBucketStorageErrorToClearConfigurationMessage() {
        ErrorType result = S3ServiceImpl.mapStorageErrorCode("NoSuchBucket", ErrorType.FILE_UPLOAD_FAILED);

        assertThat(result).isEqualTo(ErrorType.FILE_STORAGE_CONFIGURATION_FAILED);
    }
}
