package com.fos.workspace.document;

import com.fos.sdk.storage.StorageOperationException;
import com.fos.workspace.config.GlobalExceptionHandler;
import com.fos.workspace.document.api.DocumentController;
import com.fos.workspace.document.application.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerErrorHandlingTest {

    private MockMvc mockMvc;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DocumentController(documentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn500WhenUploadInitiationFailsInStorageLayer() throws Exception {
        when(documentService.initiateUpload(any()))
                .thenThrow(new StorageOperationException(
                        "MinIO upload URL generation failed for objectKey=documents/test-club/test-document/test.pdf"));

        mockMvc.perform(post("/api/v1/documents/upload/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Training Plan",
                                  "description": null,
                                  "category": "GENERAL",
                                  "visibility": "CLUB_WIDE",
                                  "originalFilename": "SEANCE D'ENTRAINEMENT MARDI LE 19 MAI 2026 NUM 212.pdf",
                                  "contentType": "application/pdf",
                                  "fileSizeBytes": 2048,
                                  "linkedPlayerRefId": null,
                                  "linkedTeamRefId": null,
                                  "tags": [],
                                  "versionNote": null
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORAGE_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "MinIO upload URL generation failed for objectKey=documents/test-club/test-document/test.pdf"));
    }
}
