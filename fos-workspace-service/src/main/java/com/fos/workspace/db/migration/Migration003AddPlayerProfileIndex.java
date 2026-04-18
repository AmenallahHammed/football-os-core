package com.fos.workspace.db.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;

@ChangeUnit(id = "migration-003-player-profile-compound-index", order = "003", author = "fos-team")
public class Migration003AddPlayerProfileIndex {

    private static final String COLLECTION = "workspace_documents";
    private static final String INDEX_NAME = "idx_workspace_documents_player_profile";

    @Execution
    public void addCompoundIndex(MongoTemplate mongoTemplate) {
        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document()
                        .append("linkedPlayerRef.id", 1)
                        .append("category", 1)
                        .append("state", 1))
                .named(INDEX_NAME));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION).dropIndex(INDEX_NAME);
    }
}
