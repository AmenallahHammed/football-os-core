package com.fos.sdk.storage.adapter;

import com.fos.sdk.storage.PresignedUploadUrl;
import com.fos.sdk.storage.StoragePort;
import io.minio.*;
import io.minio.http.Method;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "fos.storage.provider", havingValue = "minio")
public class MinioStorageAdapter implements StoragePort {

    private final MinioClient minioClient;
    private final String defaultBucket;

    public MinioStorageAdapter(
            @org.springframework.beans.factory.annotation.Value("${minio.endpoint:http://localhost:9000}") String endpoint,
            @org.springframework.beans.factory.annotation.Value("${minio.access-key:minioadmin}") String accessKey,
            @org.springframework.beans.factory.annotation.Value("${minio.secret-key:minioadmin}") String secretKey,
            @org.springframework.beans.factory.annotation.Value("${minio.bucket:fos-files}") String bucket) {
        this.minioClient = MinioClient.builder()
            .endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.defaultBucket = bucket;
    }

    @Override
    public PresignedUploadUrl generateUploadUrl(String bucket, String objectKey,
                                                String contentType, Duration expiry) {
        try {
            ensureBucket(bucket);
            String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT).bucket(bucket).object(objectKey)
                    .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS).build());
            return new PresignedUploadUrl(url, objectKey, Instant.now().plus(expiry));
        } catch (Exception e) {
            throw new IllegalStateException("MinIO upload URL generation failed for: " + objectKey, e);
        }
    }

    @Override
    public String generateDownloadUrl(String bucket, String objectKey, Duration expiry) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(objectKey)
                    .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS).build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO download URL generation failed for: " + objectKey, e);
        }
    }

    @Override
    public void putObject(String bucket, String objectKey, InputStream content,
                          long contentLength, String contentType) {
        try {
            ensureBucket(bucket);

            PutObjectArgs.Builder args = PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(content, contentLength, -1);

            if (contentType != null && !contentType.isBlank()) {
                args.contentType(contentType);
            }

            minioClient.putObject(args.build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO upload failed for: " + objectKey, e);
        }
    }

    @Override
    public void confirmUpload(String bucket, String objectKey) { /* no-op */ }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO delete failed for: " + objectKey, e);
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
