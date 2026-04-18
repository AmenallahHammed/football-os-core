package com.fos.workspace.profile.api;

import com.fos.workspace.document.api.DocumentResponse;

import java.util.List;
import java.util.UUID;

public record PlayerProfileResponse(
        UUID playerId,
        String playerName,
        String position,
        String nationality,
        String dateOfBirth,
        UUID currentTeamId,
        List<DocumentResponse> documents,
        List<DocumentResponse> reports,
        List<DocumentResponse> medicalRecords,
        List<DocumentResponse> adminDocuments,
        int documentCount,
        int reportCount,
        int medicalRecordCount,
        int adminDocumentCount
) {
}
