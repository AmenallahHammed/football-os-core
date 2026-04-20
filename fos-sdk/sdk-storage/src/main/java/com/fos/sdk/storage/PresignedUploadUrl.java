package com.fos.sdk.storage;

import java.time.Instant;
import java.util.function.IntPredicate;

public record PresignedUploadUrl(String uploadUrl, String objectKey, Instant expiresAt) {

    public IntPredicate url() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'url'");
    }
}
