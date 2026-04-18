package com.fos.workspace.event.domain;

import java.util.UUID;

/**
 * A document requirement that must be fulfilled before an event.
 */
public class RequiredDocument {

    private UUID requirementId;
    private String description;
    private String documentCategory;
    private UUID assignedToActorId;
    private UUID submittedDocumentId;
    private boolean submitted;

    protected RequiredDocument() {
    }

    public RequiredDocument(String description, String documentCategory, UUID assignedToActorId) {
        this.requirementId = UUID.randomUUID();
        this.description = description;
        this.documentCategory = documentCategory;
        this.assignedToActorId = assignedToActorId;
        this.submitted = false;
    }

    public void markSubmitted(UUID documentId) {
        this.submittedDocumentId = documentId;
        this.submitted = true;
    }

    public UUID getRequirementId() {
        return requirementId;
    }

    public String getDescription() {
        return description;
    }

    public String getDocumentCategory() {
        return documentCategory;
    }

    public UUID getAssignedToActorId() {
        return assignedToActorId;
    }

    public UUID getSubmittedDocumentId() {
        return submittedDocumentId;
    }

    public boolean isSubmitted() {
        return submitted;
    }
}
