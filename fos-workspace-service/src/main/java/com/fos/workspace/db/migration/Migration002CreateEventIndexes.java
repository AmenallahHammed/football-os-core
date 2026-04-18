package com.fos.workspace.db.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

@ChangeUnit(id = "migration-002-create-event-indexes", order = "002", author = "fos-team")
public class Migration002CreateEventIndexes {

    private static final String COLLECTION = "workspace_events";

    @Execution
    public void createIndexes(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            mongoTemplate.createCollection(COLLECTION);
        }

        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);
        ops.ensureIndex(new Index().on("teamRef.id", Sort.Direction.ASC).named("idx_workspace_events_team_id"));
        ops.ensureIndex(new Index().on("startAt", Sort.Direction.ASC).named("idx_workspace_events_start_at"));
        ops.ensureIndex(new Index().on("createdByRef.id", Sort.Direction.ASC).named("idx_workspace_events_created_by"));
        ops.ensureIndex(new Index().on("type", Sort.Direction.ASC).named("idx_workspace_events_type"));
        ops.ensureIndex(new Index().on("state", Sort.Direction.ASC).named("idx_workspace_events_state"));
        ops.ensureIndex(new Index()
                .on("state", Sort.Direction.ASC)
                .on("reminderSent", Sort.Direction.ASC)
                .on("startAt", Sort.Direction.ASC)
                .named("idx_workspace_events_reminder_query"));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);
        ops.dropIndex("idx_workspace_events_team_id");
        ops.dropIndex("idx_workspace_events_start_at");
        ops.dropIndex("idx_workspace_events_created_by");
        ops.dropIndex("idx_workspace_events_type");
        ops.dropIndex("idx_workspace_events_state");
        ops.dropIndex("idx_workspace_events_reminder_query");
    }
}
