package com.fos.sdk.storage.adapter;

import com.fos.sdk.storage.PresignedUploadUrl;
import com.fos.sdk.storage.StoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "fos.storage.provider", havingValue = "azure")
public class AzureBlobStorageAdapter implements StoragePort {
    @Override public PresignedUploadUrl generateUploadUrl(String b, String k, String c, Duration e) { throw new UnsupportedOperationException("AzureBlobStorageAdapter not yet implemented"); }
    @Override public String generateDownloadUrl(String b, String k, Duration e) { throw new UnsupportedOperationException(); }
    @Override public void putObject(String b, String k, InputStream c, long l, String t) { throw new UnsupportedOperationException(); }
    @Override public void confirmUpload(String b, String k) { throw new UnsupportedOperationException(); }
    @Override public void deleteObject(String b, String k) { throw new UnsupportedOperationException(); }
}
