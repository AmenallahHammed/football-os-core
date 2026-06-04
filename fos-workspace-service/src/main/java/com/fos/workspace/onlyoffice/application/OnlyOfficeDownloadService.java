package com.fos.workspace.onlyoffice.application;

import com.fos.sdk.storage.StoragePort;
import com.fos.workspace.document.domain.DocumentVersion;
import com.fos.workspace.document.domain.WorkspaceDocument;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OnlyOfficeDownloadService {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeDownloadService.class);
    private static final Pattern DOCUMENT_KEY_PATTERN =
            Pattern.compile("^(?<documentId>[0-9a-fA-F\\-]{36})_v(?<version>\\d+)$");

    private final WorkspaceDocumentRepository documentRepository;
    private final StoragePort storagePort;
    private final OnlyOfficeDownloadTokenService tokenService;

    public OnlyOfficeDownloadService(WorkspaceDocumentRepository documentRepository,
                                     StoragePort storagePort,
                                     OnlyOfficeDownloadTokenService tokenService) {
        this.documentRepository = documentRepository;
        this.storagePort = storagePort;
        this.tokenService = tokenService;
    }

    public DownloadedDocument downloadDocument(String documentKey, String token) {
        log.debug("OnlyOffice download requested: documentKey={} tokenPresent={}",
                documentKey, token != null && !token.isBlank());

        if (token == null || token.isBlank()) {
            log.warn("OnlyOffice download rejected: token is missing for documentKey={}", documentKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Download token is required");
        }

        ParsedDocumentKey parsedKey;
        OnlyOfficeDownloadTokenService.DownloadTokenClaims tokenClaims;
        try {
            parsedKey = parseDocumentKey(documentKey);
            log.debug("Parsed document key: documentId={} version={}",
                    parsedKey.documentId(), parsedKey.versionNumber());
            tokenClaims = tokenService.parseDownloadToken(token);
            log.debug("Token validated: documentId={} version={}",
                    tokenClaims.documentId(), tokenClaims.versionNumber());
        } catch (ExpiredJwtException e) {
            log.warn("OnlyOffice download token has expired: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Download token has expired", e);
        } catch (SignatureException e) {
            log.warn("OnlyOffice download token has invalid signature: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid download token signature", e);
        } catch (MalformedJwtException e) {
            log.warn("OnlyOffice download token is malformed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Malformed download token", e);
        } catch (JwtException e) {
            log.warn("OnlyOffice download token validation failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid download token", e);
        } catch (IllegalArgumentException e) {
            log.warn("OnlyOffice download token contains invalid data: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, 
                    "Invalid token data: " + e.getMessage(), e);
        }

        if (!parsedKey.documentId().equals(tokenClaims.documentId()) ||
            parsedKey.versionNumber() != tokenClaims.versionNumber()) {
            log.warn("OnlyOffice download token mismatch: pathDocumentId={} pathVersion={} tokenDocumentId={} tokenVersion={}",
                    parsedKey.documentId(), parsedKey.versionNumber(),
                    tokenClaims.documentId(), tokenClaims.versionNumber());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, 
                    "Download token does not match the requested document version");
        }

        WorkspaceDocument document = documentRepository.findByResourceId(parsedKey.documentId())
                .orElseThrow(() -> {
                    log.error("OnlyOffice download: document not found: {}", parsedKey.documentId());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
                });

        DocumentVersion version = document.getVersions().stream()
                .filter(item -> item.getVersionNumber() == parsedKey.versionNumber())
                .findFirst()
                .orElseThrow(() -> {
                    log.error("OnlyOffice download: document version not found: {} v{}",
                            parsedKey.documentId(), parsedKey.versionNumber());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Document version not found");
                });

        log.info("OnlyOffice download authorized: documentKey={} documentId={} version={} bucket={} objectKey={} contentType={} fileSize={}",
                documentKey,
                parsedKey.documentId(),
                parsedKey.versionNumber(),
                version.getStorageBucket(),
                version.getStorageObjectKey(),
                version.getContentType(),
                version.getFileSizeBytes());

        byte[] fileBytes = storagePort.getObject(version.getStorageBucket(), version.getStorageObjectKey());
        if (fileBytes.length == 0) {
            log.error("OnlyOffice download: storage returned empty file for documentKey={}", documentKey);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Downloaded object is empty");
        }

        log.info("OnlyOffice download streaming: documentId={} version={} bytes={}",
                parsedKey.documentId(),
                parsedKey.versionNumber(),
                fileBytes.length);

        return new DownloadedDocument(
                version.getOriginalFilename(),
                version.getContentType(),
                version.getFileSizeBytes(),
                fileBytes);
    }

    private ParsedDocumentKey parseDocumentKey(String documentKey) {
        Matcher matcher = DOCUMENT_KEY_PATTERN.matcher(documentKey == null ? "" : documentKey.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid OnlyOffice document key: " + documentKey);
        }

        return new ParsedDocumentKey(
                UUID.fromString(matcher.group("documentId")),
                Integer.parseInt(matcher.group("version")));
    }

    private record ParsedDocumentKey(UUID documentId, int versionNumber) {
    }

    public record DownloadedDocument(
            String originalFilename,
            String contentType,
            Long fileSizeBytes,
            byte[] bytes
    ) {
    }
}
