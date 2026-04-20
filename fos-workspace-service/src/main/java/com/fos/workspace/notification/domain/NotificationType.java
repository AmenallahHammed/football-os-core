package com.fos.workspace.notification.domain;

/**
 * What kind of notification this is.
 * Used by the frontend to choose the correct icon and message template.
 */
public enum NotificationType {
    DOCUMENT_MISSING,      // Actor must submit a required document for an event
    DOCUMENT_UPLOADED,     // A document linked to this actor was uploaded
    EVENT_REMINDER,        // An event is starting soon
    TASK_ASSIGNED,         // A task was assigned to this actor
    GENERAL
}
