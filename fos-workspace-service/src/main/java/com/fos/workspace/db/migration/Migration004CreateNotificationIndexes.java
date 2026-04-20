package com.fos.workspace.db.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

@ChangeUnit(id = "migration-004-create-notification-indexes", order = "004", author = "fos-team")
public class Migration004CreateNotificationIndexes {

    private static final String COLLECTION = "workspace_notifications";

    @Execution
    public void createIndexes(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            mongoTemplate.createCollection(COLLECTION);
        }

        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);

        // Primary inbox query: find all notifications for an actor, sorted by date
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document()
                        .append("recipientActorId", 1)
                        .append("createdAt", -1))
                .named("idx_notifications_recipient_date"));

        // Unread count query
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document()
                        .append("recipientActorId", 1)
                        .append("read", 1))
                .named("idx_notifications_recipient_read"));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);
        ops.dropIndex("idx_notifications_recipient_date");
        ops.dropIndex("idx_notifications_recipient_read");
    }
}
