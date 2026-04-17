package com.fos.governance.identity.domain;

/**
 * All valid actor roles in Football OS.
 * Values must match FosRoles constants in sdk-core.
 */
public enum ActorRole {
    PLAYER,
    HEAD_COACH,
    ASSISTANT_COACH,
    GOALKEEPER_COACH,
    PHYSICAL_TRAINER,
    MEDICAL_STAFF,
    ANALYST,
    CLUB_ADMIN,
    OPERATOR
}
