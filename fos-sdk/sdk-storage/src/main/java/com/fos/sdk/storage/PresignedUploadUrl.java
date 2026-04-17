package com.fos.sdk.storage;

import java.time.Instant;

public record PresignedUploadUrl(String uploadUrl, String objectKey, Instant expiresAt) {}
