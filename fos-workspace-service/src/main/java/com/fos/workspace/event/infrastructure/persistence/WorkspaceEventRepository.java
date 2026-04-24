package com.fos.workspace.event.infrastructure.persistence;

import com.fos.sdk.core.ResourceState;
import com.fos.workspace.event.domain.EventType;
import com.fos.workspace.event.domain.WorkspaceEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceEventRepository extends MongoRepository<WorkspaceEvent, String> {

    Optional<WorkspaceEvent> findByResourceIdAndCreatedByRefId(UUID resourceId, UUID createdByRefId);

    Page<WorkspaceEvent> findByCreatedByRefIdAndTeamRefIdAndStateOrderByStartAtAsc(UUID createdByRefId,
                                                                                     UUID teamRefId,
                                                                                     ResourceState state,
                                                                                     Pageable pageable);

    Page<WorkspaceEvent> findByTypeAndStateOrderByStartAtAsc(EventType type,
                                                              ResourceState state,
                                                              Pageable pageable);

    Page<WorkspaceEvent> findByCreatedByRefIdAndState(UUID createdByRefId,
                                                       ResourceState state,
                                                       Pageable pageable);

    @Query("{ 'createdByRef.id': ?0, 'state': 'ACTIVE', 'title': { $regex: ?1, $options: 'i' } }")
    List<WorkspaceEvent> searchActiveByClubAndTitle(UUID createdByRefId, String titlePattern);

    @Query("{ 'state': 'ACTIVE', 'reminderSent': false, 'startAt': { $gte: ?0, $lte: ?1 } }")
    List<WorkspaceEvent> findUpcomingEventsNeedingReminder(Instant from, Instant to);
}
