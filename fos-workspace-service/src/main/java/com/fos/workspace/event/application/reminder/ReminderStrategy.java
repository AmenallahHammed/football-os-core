package com.fos.workspace.event.application.reminder;

import com.fos.workspace.event.domain.WorkspaceEvent;

/**
 * Strategy interface for event reminder logic.
 */
public interface ReminderStrategy {

    boolean shouldRemind(WorkspaceEvent event);

    void sendReminder(WorkspaceEvent event);
}
