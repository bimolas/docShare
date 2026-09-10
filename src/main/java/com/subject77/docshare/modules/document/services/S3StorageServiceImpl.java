package com.subject77.docshare.modules.document.services;

import com.subject77.docshare.exceptions.StorageException;
import com.subject77.docshare.exceptions.StorageFileNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageServiceImpl implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name:docshare-bucket}")
    private String defaultBucketName;

    @Override
    public String generatePresignedUploadUrl(String bucket, String objectKey, String contentType, long durationMinutes) {
        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : defaultBucketName;

        try {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(durationMinutes))
                    .putObjectRequest(objectRequest)
                    .build();

            String url = s3Presigner.presignPutObject(presignRequest).url().toString();
            log.info("Generated presigned upload URL for key: {}", objectKey);
            return url;
        } catch (SdkException e) {
            log.error("Failed to generate presigned upload URL for key: {}", objectKey, e);
            throw new StorageException("Failed to generate presigned upload URL for key: " + objectKey, e);
        }
    }

    @Override
    public String generatePresignedDownloadUrl(String bucket, String objectKey, String originalFileName, long durationMinutes) {
        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : defaultBucketName;
        
        // Verify object exists before presigning download
        if (!doesObjectExist(targetBucket, objectKey)) {
            throw new StorageFileNotFoundException("Object not found in storage key: " + objectKey);
        }

        try {
            String contentDisposition = "attachment; filename=\"" + originalFileName + "\"";

            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(objectKey)
                    .responseContentDisposition(contentDisposition)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(durationMinutes))
                    .getObjectRequest(objectRequest)
                    .build();

            String url = s3Presigner.presignGetObject(presignRequest).url().toString();
            log.info("Generated presigned download URL for key: {}", objectKey);
            return url;
        } catch (SdkException e) {
            log.error("Failed to generate presigned download URL for key: {}", objectKey, e);
            throw new StorageException("Failed to generate presigned download URL for key: " + objectKey, e);
        }
    }

    @Override
    public String generatePresignedPreviewUrl(String bucket, String objectKey, String originalFileName, String contentType, long durationMinutes) {
        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : defaultBucketName;

        // Verify object exists before presigning preview
        if (!doesObjectExist(targetBucket, objectKey)) {
            throw new StorageFileNotFoundException("Object not found in storage key: " + objectKey);
        }

        try {
            String contentDisposition = "inline; filename=\"" + originalFileName + "\"";

            GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(objectKey)
                    .responseContentDisposition(contentDisposition);

            if (contentType != null && !contentType.isBlank()) {
                requestBuilder.responseContentType(contentType);
            }

            GetObjectRequest objectRequest = requestBuilder.build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(durationMinutes))
                    .getObjectRequest(objectRequest)
                    .build();

            String url = s3Presigner.presignGetObject(presignRequest).url().toString();
            log.info("Generated presigned preview URL for key: {}", objectKey);
            return url;
        } catch (SdkException e) {
            log.error("Failed to generate presigned preview URL for key: {}", objectKey, e);
            throw new StorageException("Failed to generate presigned preview URL for key: " + objectKey, e);
        }
    }

    @Override
    public boolean doesObjectExist(String bucket, String objectKey) {
        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : defaultBucketName;
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (SdkException e) {
            log.error("Infrastructure error checking object existence for key: {}", objectKey, e);
            throw new StorageException("Infrastructure error communicating with S3 storage", e);
        }
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : defaultBucketName;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(objectKey)
                    .build());
            log.info("Deleted object key: {}", objectKey);
        } catch (SdkException e) {
            log.error("Failed to delete object from storage key: {}", objectKey, e);
            throw new StorageException("Failed to delete object from storage key: " + objectKey, e);
        }
    }

    @Override
    public void streamObject(String bucket, String objectKey, java.io.OutputStream outputStream) {
        String targetBucket = (bucket != null && !bucket.isBlank()) ? bucket : defaultBucketName;
        try {
            s3Client.getObject(GetObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(objectKey)
                    .build(), software.amazon.awssdk.core.sync.ResponseTransformer.toOutputStream(outputStream));
        } catch (NoSuchKeyException e) {
            throw new StorageFileNotFoundException("Object not found in storage key: " + objectKey);
        } catch (SdkException e) {
            log.error("Failed to stream object from storage key: {}", objectKey, e);
            throw new StorageException("Failed to stream object from storage key: " + objectKey, e);
        }
    }

    @Override
    public String getDefaultBucketName() {
        return defaultBucketName;
    }
}

