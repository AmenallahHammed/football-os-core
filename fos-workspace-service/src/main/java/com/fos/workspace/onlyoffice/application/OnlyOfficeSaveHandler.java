package com.fos.workspace.onlyoffice.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import com.fos.sdk.storage.StoragePort;
import com.fos.workspace.document.domain.DocumentVersion;
import com.fos.workspace.document.domain.WorkspaceDocument;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.util.UUID;

/**
 * Handles the OnlyOffice save callback.
 *
 * When a user finishes editing a document in OnlyOffice and it auto-saves or
 * the user clicks save, OnlyOffice Document Server calls our callback URL with
 * a JSON body like:
 *
 * {
 *   "status": 2,      <- 2 = document is ready to save
 *   "url": "https://onlyoffice-server/..."  <- download the new file from here
 * }
 *
 * We respond with {"error": 0} to acknowledge.
 *
 * Status codes from OnlyOffice:
 *   1 = document is being edited (no save yet)
 *   2 = document ready to save (download from url)
 *   3 = document save error
 *   6 = document being edited, but saving is forced
 *   7 = error during forced save
 */
@Service
public class OnlyOfficeSaveHandler {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeSaveHandler.class);

    private final WorkspaceDocumentRepository documentRepository;
    private final StoragePort storagePort;
    private final FosKafkaProducer kafkaProducer;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public OnlyOfficeSaveHandler(WorkspaceDocumentRepository documentRepository,
                                  StoragePort storagePort,
                                  FosKafkaProducer kafkaProducer,
                                  ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.storagePort = storagePort;
        this.kafkaProducer = kafkaProducer;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Processes the OnlyOffice callback.
     *
     * @param documentId the workspace document resource ID (from the URL path)
     * @param callbackBody the raw JSON body from OnlyOffice
     */
    public void handleCallback(UUID documentId, String callbackBody) {
        try {
            JsonNode payload = objectMapper.readTree(callbackBody);
            int status = payload.get("status").asInt();

            // Status 2 = ready to save (most common save trigger)
            // Status 6 = forced save
            if (status != 2 && status != 6) {
                log.debug("OnlyOffice callback: status={} - not a save event, ignoring", status);
                return;
            }

            String downloadUrl = payload.get("url").asText();
            log.info("OnlyOffice save callback: documentId={} status={} url={}",
                    documentId, status, downloadUrl);

            // -- 1. Load the document ----------------------------------------
            WorkspaceDocument document = documentRepository
                    .findByResourceId(documentId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Document not found for OnlyOffice callback: " + documentId));

            if (document.currentVersion() == null) return;

            // -- 2. Download the saved file from OnlyOffice -------------------
            byte[] fileBytes = restTemplate.getForObject(downloadUrl, byte[].class);
            if (fileBytes == null || fileBytes.length == 0) {
                log.warn("OnlyOffice returned empty file for documentId={}", documentId);
                return;
            }

            // -- 3. Generate new MinIO object key for this version ------------
            int newVersionNumber = document.nextVersionNumber();
            String oldKey = document.currentVersion().getStorageObjectKey();
            // Replace the version segment: "documents/uuid/v1_file.docx" -> "v2_file.docx"
            String newKey = oldKey.replaceFirst("v\\d+_", "v" + newVersionNumber + "_");
            String bucket = document.currentVersion().getStorageBucket();
            String contentType = document.currentVersion().getContentType();

            // -- 4. Upload to storage before writing version metadata ----------
            try (ByteArrayInputStream content = new ByteArrayInputStream(fileBytes)) {
                storagePort.putObject(bucket, newKey, content, fileBytes.length, contentType);
            }
            storagePort.confirmUpload(bucket, newKey);
            log.info("OnlyOffice save upload completed: documentId={} newVersion={} key={}",
                    documentId, newVersionNumber, newKey);

            // -- 5. Create a new DocumentVersion -----------------------------
            DocumentVersion newVersion = new DocumentVersion(
                    newKey, bucket,
                    document.currentVersion().getOriginalFilename(),
                    document.currentVersion().getContentType(),
                    (long) fileBytes.length,
                    newVersionNumber,
                    null, // OnlyOffice save - uploader is the editing actor (not tracked here yet)
                    "Auto-saved via OnlyOffice");

            document.addVersion(newVersion);
            documentRepository.save(document);

            // -- 6. Emit AUDIT signal -----------------------------------------
            kafkaProducer.emit(SignalEnvelope.builder()
                    .type(SignalType.AUDIT)
                    .topic(KafkaTopics.AUDIT_ALL)
                    .actorRef(documentId.toString())
                    .build());

        } catch (Exception e) {
            log.error("Failed to process OnlyOffice callback for documentId={}: {}",
                    documentId, e.getMessage(), e);
            // Do NOT rethrow - OnlyOffice needs us to return 200 regardless
        }
    }
}
