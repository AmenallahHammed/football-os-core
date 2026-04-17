package com.fos.sdk.storage;

import java.time.Duration;

public interface StoragePort {
    PresignedUploadUrl generateUploadUrl(String bucket, String objectKey,
                                         String contentType, Duration expiry);
    String generateDownloadUrl(String bucket, String objectKey, Duration expiry);
    void confirmUpload(String bucket, String objectKey);
    void deleteObject(String bucket, String objectKey);
}
