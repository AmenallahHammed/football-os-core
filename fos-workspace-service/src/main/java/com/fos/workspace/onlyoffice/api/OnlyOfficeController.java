package com.fos.workspace.onlyoffice.api;

import com.fos.workspace.onlyoffice.application.OnlyOfficeConfigService;
import com.fos.workspace.onlyoffice.application.OnlyOfficeSaveHandler;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onlyoffice")
public class OnlyOfficeController {

    private final OnlyOfficeConfigService configService;
    private final OnlyOfficeSaveHandler saveHandler;

    public OnlyOfficeController(OnlyOfficeConfigService configService,
                                OnlyOfficeSaveHandler saveHandler) {
        this.configService = configService;
        this.saveHandler = saveHandler;
    }

    @PostMapping("/config")
    public OnlyOfficeConfigResponse getEditorConfig(@Valid @RequestBody OnlyOfficeConfigRequest request) {
        return configService.generateConfig(request);
    }

    @PostMapping("/callback/{documentId}")
    public String handleSaveCallback(@PathVariable UUID documentId,
                                     @RequestBody String callbackBody) {
        saveHandler.handleCallback(documentId, callbackBody);
        return "{\"error\": 0}";
    }
}
