package com.fos.workspace.db.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

@ChangeUnit(id = "migration-001-create-document-indexes", order = "001", author = "fos-team")
public class Migration001CreateDocumentIndexes {

    private static final String COLLECTION = "workspace_documents";

    @Execution
    public void createIndexes(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            mongoTemplate.createCollection(COLLECTION);
        }

        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);
        ops.ensureIndex(new Index().on("ownerRef.id", Sort.Direction.ASC).named("idx_workspace_documents_owner_id"));
        ops.ensureIndex(new Index().on("category", Sort.Direction.ASC).named("idx_workspace_documents_category"));
        ops.ensureIndex(new Index().on("linkedPlayerRef.id", Sort.Direction.ASC).named("idx_workspace_documents_linked_player"));
        ops.ensureIndex(new Index().on("state", Sort.Direction.ASC).named("idx_workspace_documents_state"));
        ops.ensureIndex(new Index().on("createdAt", Sort.Direction.DESC).named("idx_workspace_documents_created_at"));
        ops.ensureIndex(new Index().on("name", Sort.Direction.ASC).named("idx_workspace_documents_name"));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);
        ops.dropIndex("idx_workspace_documents_owner_id");
        ops.dropIndex("idx_workspace_documents_category");
        ops.dropIndex("idx_workspace_documents_linked_player");
        ops.dropIndex("idx_workspace_documents_state");
        ops.dropIndex("idx_workspace_documents_created_at");
        ops.dropIndex("idx_workspace_documents_name");
    }
}
