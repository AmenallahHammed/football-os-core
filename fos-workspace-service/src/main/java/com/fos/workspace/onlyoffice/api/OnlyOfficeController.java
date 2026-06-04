package com.fos.workspace.onlyoffice.api;

import com.fos.workspace.onlyoffice.application.OnlyOfficeConfigService;
import com.fos.workspace.onlyoffice.application.OnlyOfficeDownloadService;
import com.fos.workspace.onlyoffice.application.OnlyOfficeSaveHandler;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onlyoffice")
public class OnlyOfficeController {

    private final OnlyOfficeConfigService configService;
    private final OnlyOfficeDownloadService downloadService;
    private final OnlyOfficeSaveHandler saveHandler;

    public OnlyOfficeController(OnlyOfficeConfigService configService,
                                OnlyOfficeDownloadService downloadService,
                                OnlyOfficeSaveHandler saveHandler) {
        this.configService = configService;
        this.downloadService = downloadService;
        this.saveHandler = saveHandler;
    }

    @PostMapping("/config")
    public OnlyOfficeConfigResponse getEditorConfig(@Valid @RequestBody OnlyOfficeConfigRequest request) {
        return configService.generateConfig(request);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/download/{documentKey}")
    public ResponseEntity<ByteArrayResource> downloadDocument(@PathVariable String documentKey,
                                                              @RequestParam(name = "token", required = false) String token) {
        OnlyOfficeDownloadService.DownloadedDocument downloaded = downloadService.downloadDocument(documentKey, token);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (downloaded.contentType() != null && !downloaded.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(downloaded.contentType());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(downloaded.originalFilename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentLength(downloaded.bytes().length)
                .body(new ByteArrayResource(downloaded.bytes()));
    }

    @PostMapping("/callback/{documentId}")
    public Map<String, Integer> handleSaveCallback(@PathVariable UUID documentId,
                                                   @RequestBody String callbackBody) {
        saveHandler.handleCallback(documentId, callbackBody);
        return Map.of("error", 0);
    }
}
