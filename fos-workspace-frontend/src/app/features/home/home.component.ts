import { Component } from '@angular/core';
import { WorkspaceDataService } from '../../core/data/workspace-data.service';
import { CalendarEvent, EventCreateRequest, EventParticipant } from '../../shared/models/event.model';
import { CalendarComponent } from '../calendar/calendar.component';
import { DayPanelComponent } from './day-panel/day-panel.component';
import { EventCreateComponent } from './event-create/event-create.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CalendarComponent, DayPanelComponent, EventCreateComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent {
  protected events: CalendarEvent[] = [];
  protected upcomingEvents: CalendarEvent[] = [];
  protected participants: EventParticipant[] = [];

  protected selectedDay = new Date();
  protected selectedDayEvents: CalendarEvent[] = [];

  protected dayPanelOpen = false;
  protected eventCreateOpen = false;

  constructor(private readonly workspaceData: WorkspaceDataService) {
    this.refreshData();
    this.refreshSelectedDayEvents();
  }

  protected onDaySelected(date: Date): void {
    this.selectedDay = date;
    this.refreshSelectedDayEvents();
    this.dayPanelOpen = true;
  }

  protected closeDayPanel(): void {
    this.dayPanelOpen = false;
  }

  protected openEventCreate(): void {
    this.eventCreateOpen = true;
  }

  protected closeEventCreate(): void {
    this.eventCreateOpen = false;
  }

  protected onEventCreated(request: EventCreateRequest): void {
    this.workspaceData.addCalendarEvent(request);
    this.refreshData();
    this.refreshSelectedDayEvents();
    this.eventCreateOpen = false;
    this.dayPanelOpen = true;
  }

  protected onDocumentUploaded(eventData: { eventId: string; documentId: string }): void {
    this.workspaceData.markRequiredDocumentUploaded(eventData.eventId, eventData.documentId);
    this.refreshData();
    this.refreshSelectedDayEvents();
  }

  protected onDocumentRenamed(eventData: { eventId: string; documentId: string; name: string }): void {
    this.workspaceData.renameRequiredDocument(eventData.eventId, eventData.documentId, eventData.name);
    this.refreshData();
    this.refreshSelectedDayEvents();
  }

  protected onDocumentDeleted(eventData: { eventId: string; documentId: string }): void {
    this.workspaceData.deleteRequiredDocument(eventData.eventId, eventData.documentId);
    this.refreshData();
    this.refreshSelectedDayEvents();
  }

  private refreshData(): void {
    this.events = this.workspaceData.getCalendarEvents();
    this.upcomingEvents = this.workspaceData.getUpcomingEvents(5);
    this.participants = this.workspaceData.getParticipants();
  }

  private refreshSelectedDayEvents(): void {
    this.selectedDayEvents = this.workspaceData.getEventsForDate(this.selectedDay);
  }
}
