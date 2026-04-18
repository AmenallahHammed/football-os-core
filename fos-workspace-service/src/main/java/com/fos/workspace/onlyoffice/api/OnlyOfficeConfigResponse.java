package com.fos.workspace.onlyoffice.api;

public record OnlyOfficeConfigResponse(
        String documentServerUrl,
        OnlyOfficeConfig config,
        String token
) {
    public record OnlyOfficeConfig(
            DocumentConfig document,
            EditorConfig editorConfig,
            String documentType,
            String token
    ) {
    }

    public record DocumentConfig(
            String fileType,
            String key,
            String title,
            String url
    ) {
    }

    public record EditorConfig(
            String callbackUrl,
            String lang,
            UserConfig user,
            String mode,
            CustomizationConfig customization
    ) {
    }

    public record UserConfig(String id, String name) {
    }

    public record CustomizationConfig(boolean autosave, boolean forcesave) {
    }
}
