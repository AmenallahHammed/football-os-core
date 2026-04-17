package com.fos.workspace.document.domain;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.core.BaseDocument;
import com.fos.sdk.core.ResourceState;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "workspace_documents")
public class WorkspaceDocument extends BaseDocument {

    private String name;
    private String description;
    private DocumentCategory category;
    private DocumentVisibility visibility;
    private CanonicalRef ownerRef;
    private CanonicalRef linkedPlayerRef;
    private CanonicalRef linkedTeamRef;
    private List<DocumentVersion> versions = new ArrayList<>();
    private List<String> tags = new ArrayList<>();

    protected WorkspaceDocument() {
    }

    public static WorkspaceDocument create(String name,
                                           String description,
                                           DocumentCategory category,
                                           DocumentVisibility visibility,
                                           CanonicalRef ownerRef,
                                           CanonicalRef linkedPlayerRef,
                                           CanonicalRef linkedTeamRef,
                                           List<String> tags) {
        WorkspaceDocument doc = new WorkspaceDocument();
        doc.initId();
        doc.name = name;
        doc.description = description;
        doc.category = category;
        doc.visibility = visibility;
        doc.ownerRef = ownerRef;
        doc.linkedPlayerRef = linkedPlayerRef;
        doc.linkedTeamRef = linkedTeamRef;
        if (tags != null) {
            doc.tags = new ArrayList<>(tags);
        }
        return doc;
    }

    public void addVersion(DocumentVersion version) {
        versions.add(version);
        if (getState() == ResourceState.DRAFT) {
            activate();
        }
    }

    public void softDelete() {
        archive();
    }

    public DocumentVersion currentVersion() {
        return versions.isEmpty() ? null : versions.get(versions.size() - 1);
    }

    public int nextVersionNumber() {
        return versions.size() + 1;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public DocumentCategory getCategory() { return category; }
    public DocumentVisibility getVisibility() { return visibility; }
    public CanonicalRef getOwnerRef() { return ownerRef; }
    public CanonicalRef getLinkedPlayerRef() { return linkedPlayerRef; }
    public CanonicalRef getLinkedTeamRef() { return linkedTeamRef; }
    public List<DocumentVersion> getVersions() { return List.copyOf(versions); }
    public List<String> getTags() { return List.copyOf(tags); }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setTags(List<String> tags) { this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags); }
    public void setVisibility(DocumentVisibility visibility) { this.visibility = visibility; }
}
