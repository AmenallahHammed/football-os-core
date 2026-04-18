package com.fos.workspace.onlyoffice.api;

import com.fos.workspace.onlyoffice.application.OnlyOfficeConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onlyoffice")
public class OnlyOfficeController {

    private final OnlyOfficeConfigService configService;

    public OnlyOfficeController(OnlyOfficeConfigService configService) {
        this.configService = configService;
    }

    @PostMapping("/config")
    public OnlyOfficeConfigResponse getEditorConfig(@Valid @RequestBody OnlyOfficeConfigRequest request) {
        return configService.generateConfig(request);
    }

    @PostMapping("/callback/{documentId}")
    public String handleSaveCallback(@PathVariable String documentId,
                                     @RequestBody String callbackBody) {
        return "{\"error\": 0}";
    }
}
