package com.fos.workspace.event.application.reminder;

import com.fos.workspace.event.domain.WorkspaceEvent;
import com.fos.workspace.event.infrastructure.persistence.WorkspaceEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Periodically scans for upcoming events that need reminders.
 */
@Component
public class EventReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventReminderScheduler.class);

    private final WorkspaceEventRepository eventRepository;
    private final ReminderStrategy reminderStrategy;

    public EventReminderScheduler(WorkspaceEventRepository eventRepository,
                                  ReminderStrategy reminderStrategy) {
        this.eventRepository = eventRepository;
        this.reminderStrategy = reminderStrategy;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void sendEventReminders() {
        Instant now = Instant.now();
        Instant in25Hours = now.plus(25, ChronoUnit.HOURS);

        List<WorkspaceEvent> upcomingEvents = eventRepository.findUpcomingEventsNeedingReminder(now, in25Hours);
        log.info("Reminder check: {} upcoming events found", upcomingEvents.size());

        for (WorkspaceEvent event : upcomingEvents) {
            if (reminderStrategy.shouldRemind(event)) {
                reminderStrategy.sendReminder(event);
                event.markReminderSent();
                eventRepository.save(event);
            }
        }
    }
}
