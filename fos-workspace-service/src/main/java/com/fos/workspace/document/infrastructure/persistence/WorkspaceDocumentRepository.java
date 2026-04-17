package com.fos.workspace.document.infrastructure.persistence;

import com.fos.sdk.core.ResourceState;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.WorkspaceDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceDocumentRepository extends MongoRepository<WorkspaceDocument, String> {

    Page<WorkspaceDocument> findByOwnerRefIdAndState(UUID ownerRefId, ResourceState state, Pageable pageable);

    Page<WorkspaceDocument> findByCategoryAndState(DocumentCategory category, ResourceState state, Pageable pageable);

    List<WorkspaceDocument> findByLinkedPlayerRefIdAndState(UUID linkedPlayerRefId, ResourceState state);

    Optional<WorkspaceDocument> findByResourceId(UUID resourceId);

    boolean existsByResourceIdAndState(UUID resourceId, ResourceState state);

    @Query("{ 'name': { $regex: ?0, $options: 'i' }, 'state': 'ACTIVE' }")
    Page<WorkspaceDocument> searchByName(String namePattern, Pageable pageable);
}
