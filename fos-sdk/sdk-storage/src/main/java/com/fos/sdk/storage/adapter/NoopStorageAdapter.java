package com.fos.sdk.storage.adapter;

import com.fos.sdk.storage.PresignedUploadUrl;
import com.fos.sdk.storage.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "fos.storage.provider", havingValue = "noop", matchIfMissing = true)
public class NoopStorageAdapter implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(NoopStorageAdapter.class);

    @Override
    public PresignedUploadUrl generateUploadUrl(String bucket, String objectKey,
                                                String contentType, Duration expiry) {
        log.info("[NOOP-STORAGE] generateUploadUrl: bucket={}, key={}", bucket, objectKey);
        return new PresignedUploadUrl(
            "https://noop.fos.local/upload/" + objectKey,
            objectKey,
            Instant.now().plus(expiry)
        );
    }

    @Override
    public String generateDownloadUrl(String bucket, String objectKey, Duration expiry) {
        log.info("[NOOP-STORAGE] generateDownloadUrl: bucket={}, key={}", bucket, objectKey);
        return "https://noop.fos.local/download/" + objectKey;
    }

    @Override
    public void putObject(String bucket, String objectKey, InputStream content,
                          long contentLength, String contentType) {
        log.info("[NOOP-STORAGE] putObject: bucket={}, key={}, bytes={}, contentType={}",
                bucket, objectKey, contentLength, contentType);
    }

    @Override
    public void confirmUpload(String bucket, String objectKey) {
        log.info("[NOOP-STORAGE] confirmUpload: bucket={}, key={}", bucket, objectKey);
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        log.info("[NOOP-STORAGE] deleteObject: bucket={}, key={}", bucket, objectKey);
    }
}
