package com.fos.workspace.event.domain;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;

import java.util.UUID;

/**
 * An attendee of an event backed by a canonical reference.
 */
public class AttendeeRef {

    private CanonicalRef canonicalRef;
    private boolean mandatory;
    private boolean confirmed;

    protected AttendeeRef() {
    }

    public AttendeeRef(CanonicalRef canonicalRef, boolean mandatory) {
        this.canonicalRef = canonicalRef;
        this.mandatory = mandatory;
        this.confirmed = false;
    }

    public static AttendeeRef mandatoryPlayer(UUID playerId) {
        return new AttendeeRef(CanonicalRef.of(CanonicalType.PLAYER, playerId), true);
    }

    public static AttendeeRef optionalStaff(UUID staffActorId) {
        return new AttendeeRef(CanonicalRef.of(CanonicalType.CLUB, staffActorId), false);
    }

    public void confirm() {
        this.confirmed = true;
    }

    public CanonicalRef getCanonicalRef() {
        return canonicalRef;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}
