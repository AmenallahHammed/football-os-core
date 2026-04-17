package com.fos.sdk.storage;

import com.fos.sdk.storage.adapter.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({NoopStorageAdapter.class, MinioStorageAdapter.class,
         S3StorageAdapter.class, AzureBlobStorageAdapter.class})
public class StorageAutoConfiguration {}
