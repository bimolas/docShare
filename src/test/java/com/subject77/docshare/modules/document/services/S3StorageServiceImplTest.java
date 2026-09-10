package com.subject77.docshare.modules.document.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceImplTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @InjectMocks
    private S3StorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "defaultBucketName", "test-bucket");
    }

    @Test
    @DisplayName("Should generate presigned upload URL")
    void generatePresignedUploadUrl_Success() throws Exception {
        PresignedPutObjectRequest presignedPut = mock(PresignedPutObjectRequest.class);
        URL mockUrl = URI.create("https://minio.test-bucket.s3.com/upload-key").toURL();
        when(presignedPut.url()).thenReturn(mockUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut);

        String url = storageService.generatePresignedUploadUrl("test-bucket", "users/user-1/file.pdf", "application/pdf", 15L);

        assertThat(url).isEqualTo("https://minio.test-bucket.s3.com/upload-key");
        verify(s3Presigner).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    @DisplayName("Should generate presigned download URL")
    void generatePresignedDownloadUrl_Success() throws Exception {
        PresignedGetObjectRequest presignedGet = mock(PresignedGetObjectRequest.class);
        URL mockUrl = URI.create("https://minio.test-bucket.s3.com/download-key").toURL();
        when(presignedGet.url()).thenReturn(mockUrl);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGet);

        String url = storageService.generatePresignedDownloadUrl("test-bucket", "users/user-1/file.pdf", "file.pdf", 15L);

        assertThat(url).isEqualTo("https://minio.test-bucket.s3.com/download-key");
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    @DisplayName("Should return true when object exists in S3")
    void doesObjectExist_True() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(null);

        boolean exists = storageService.doesObjectExist("test-bucket", "some-key");

        assertThat(exists).isTrue();
        verify(s3Client).headObject(any(HeadObjectRequest.class));
    }

    @Test
    @DisplayName("Should return false when object does not exist in S3")
    void doesObjectExist_False() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

        boolean exists = storageService.doesObjectExist("test-bucket", "missing-key");

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Should delete object from S3")
    void deleteObject_Success() {
        storageService.deleteObject("test-bucket", "key-to-delete");

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}
