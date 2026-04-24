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

    Page<WorkspaceDocument> findByOwnerRefIdAndCategoryAndState(UUID ownerRefId,
                                                                 DocumentCategory category,
                                                                 ResourceState state,
                                                                 Pageable pageable);

    List<WorkspaceDocument> findByOwnerRefIdAndLinkedPlayerRefIdAndState(UUID ownerRefId,
                                                                          UUID linkedPlayerRefId,
                                                                          ResourceState state);

    Optional<WorkspaceDocument> findByResourceId(UUID resourceId);

    Optional<WorkspaceDocument> findByResourceIdAndOwnerRefId(UUID resourceId, UUID ownerRefId);

    boolean existsByResourceIdAndState(UUID resourceId, ResourceState state);

    @Query("{ 'ownerRef.id': ?0, 'name': { $regex: ?1, $options: 'i' }, 'state': 'ACTIVE' }")
    Page<WorkspaceDocument> searchByOwnerRefIdAndName(UUID ownerRefId, String namePattern, Pageable pageable);
}
