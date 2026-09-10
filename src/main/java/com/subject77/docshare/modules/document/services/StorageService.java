package com.subject77.docshare.modules.document.services;

public interface StorageService {

    String generatePresignedUploadUrl(String bucket, String objectKey, String contentType, long durationMinutes);

    String generatePresignedDownloadUrl(String bucket, String objectKey, String originalFileName, long durationMinutes);

    String generatePresignedPreviewUrl(String bucket, String objectKey, String originalFileName, String contentType, long durationMinutes);

    boolean doesObjectExist(String bucket, String objectKey);

    void deleteObject(String bucket, String objectKey);

    void streamObject(String bucket, String objectKey, java.io.OutputStream outputStream);

    String getDefaultBucketName();

    default String generatePresignedUploadUrl(String objectKey, String contentType, long durationMinutes) {
        return generatePresignedUploadUrl(getDefaultBucketName(), objectKey, contentType, durationMinutes);
    }

    default String generatePresignedDownloadUrl(String objectKey, String originalFileName, long durationMinutes) {
        return generatePresignedDownloadUrl(getDefaultBucketName(), objectKey, originalFileName, durationMinutes);
    }

    default String generatePresignedPreviewUrl(String objectKey, String originalFileName, String contentType, long durationMinutes) {
        return generatePresignedPreviewUrl(getDefaultBucketName(), objectKey, originalFileName, contentType, durationMinutes);
    }

    default boolean doesObjectExist(String objectKey) {
        return doesObjectExist(getDefaultBucketName(), objectKey);
    }

    default void deleteObject(String objectKey) {
        deleteObject(getDefaultBucketName(), objectKey);
    }

    default void streamObject(String objectKey, java.io.OutputStream outputStream) {
        streamObject(getDefaultBucketName(), objectKey, outputStream);
    }
}
