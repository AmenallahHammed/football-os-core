package com.fos.sdk.storage;

import java.io.InputStream;
import java.time.Duration;

public interface StoragePort {
    PresignedUploadUrl generateUploadUrl(String bucket, String objectKey,
                                         String contentType, Duration expiry);
    String generateDownloadUrl(String bucket, String objectKey, Duration expiry);
    default byte[] getObject(String bucket, String objectKey) {
        throw new UnsupportedOperationException("Object retrieval is not supported by this storage adapter");
    }
    void putObject(String bucket, String objectKey, InputStream content,
                   long contentLength, String contentType);
    void confirmUpload(String bucket, String objectKey);
    void deleteObject(String bucket, String objectKey);
}
