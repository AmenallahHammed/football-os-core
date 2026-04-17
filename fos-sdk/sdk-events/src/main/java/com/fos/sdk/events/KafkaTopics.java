package com.fos.sdk.events;

public final class KafkaTopics {
    // Identity
    public static final String IDENTITY_ACTOR_CREATED     = "fos.identity.actor.created";
    public static final String IDENTITY_ACTOR_UPDATED     = "fos.identity.actor.updated";
    public static final String IDENTITY_ACTOR_DEACTIVATED = "fos.identity.actor.deactivated";
    public static final String IDENTITY_ROLE_ASSIGNED     = "fos.identity.actor.role-assigned";

    // Canonical
    public static final String CANONICAL_PLAYER_CREATED   = "fos.canonical.player.created";
    public static final String CANONICAL_PLAYER_UPDATED   = "fos.canonical.player.updated";
    public static final String CANONICAL_TEAM_CREATED     = "fos.canonical.team.created";
    public static final String CANONICAL_TEAM_UPDATED     = "fos.canonical.team.updated";

    // Storage
    public static final String STORAGE_FILE_UPLOADED      = "fos.storage.file.uploaded";

    // Audit
    public static final String AUDIT_ALL                  = "fos.audit.all";

    // Signal service
    public static final String SIGNAL_ESCALATION_RAISED   = "fos.signal.escalation.raised";
    public static final String SIGNAL_ESCALATION_RESOLVED = "fos.signal.escalation.resolved";

    // DLQ convention
    public static final String DLQ_SUFFIX = ".dlq";
    public static String dlqFor(String topic) { return topic + DLQ_SUFFIX; }

    private KafkaTopics() {}
}
