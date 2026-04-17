package com.fos.workspace.document.domain;

import java.time.Instant;
import java.util.UUID;

public class DocumentVersion {

    private UUID versionId;
    private String storageObjectKey;
    private String storageBucket;
    private String originalFilename;
    private String contentType;
    private Long fileSizeBytes;
    private int versionNumber;
    private UUID uploadedByActorId;
    private Instant uploadedAt;
    private String versionNote;

    protected DocumentVersion() {
    }

    public DocumentVersion(String storageObjectKey,
                           String storageBucket,
                           String originalFilename,
                           String contentType,
                           Long fileSizeBytes,
                           int versionNumber,
                           UUID uploadedByActorId,
                           String versionNote) {
        this.versionId = UUID.randomUUID();
        this.storageObjectKey = storageObjectKey;
        this.storageBucket = storageBucket;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.versionNumber = versionNumber;
        this.uploadedByActorId = uploadedByActorId;
        this.uploadedAt = Instant.now();
        this.versionNote = versionNote;
    }

    public UUID getVersionId() { return versionId; }
    public String getStorageObjectKey() { return storageObjectKey; }
    public String getStorageBucket() { return storageBucket; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public int getVersionNumber() { return versionNumber; }
    public UUID getUploadedByActorId() { return uploadedByActorId; }
    public Instant getUploadedAt() { return uploadedAt; }
    public String getVersionNote() { return versionNote; }
}
