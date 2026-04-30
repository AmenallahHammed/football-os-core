import { Component } from '@angular/core';
import { WorkspaceDataService } from '../../core/data/workspace-data.service';
import { UserProfileComponent } from '../../core/layout/user-profile/user-profile.component';
import { CalendarEvent, EventCreateRequest, EventParticipant } from '../../shared/models/event.model';
import { SearchResult } from '../../shared/models/search.model';
import { CalendarComponent } from './calendar.component';
import { DayPanelComponent } from './day-panel/day-panel.component';
import { EventCreateComponent } from './event-create/event-create.component';

@Component({
  selector: 'app-calendar-page',
  standalone: true,
  imports: [CalendarComponent, DayPanelComponent, EventCreateComponent, UserProfileComponent],
  templateUrl: './calendar-page.component.html',
  styleUrl: './calendar-page.component.scss'
})
export class CalendarPageComponent {
  protected readonly unreadNotificationCount = this.workspaceData.unreadNotificationCount;

  protected events: CalendarEvent[] = [];
  protected upcomingEvents: CalendarEvent[] = [];
  protected participants: EventParticipant[] = [];
  protected searchResults: SearchResult[] = [];
  protected searchTerm = '';

  protected selectedDay = new Date(2026, 7, 12);
  protected selectedDayEvents: CalendarEvent[] = [];

  protected dayPanelOpen = false;
  protected eventCreateOpen = false;

  private readonly cardDateFormatter = new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric'
  });

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

  protected onSearchInput(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.searchTerm = input?.value ?? '';
    this.searchResults = this.searchTerm.trim() ? this.workspaceData.search(this.searchTerm).slice(0, 5) : [];
  }

  protected clearSearch(): void {
    this.searchTerm = '';
    this.searchResults = [];
  }

  protected eventTone(type: CalendarEvent['type']): string {
    if (type === 'Training') {
      return 'training';
    }

    if (type === 'Match' || type === 'Meeting') {
      return 'match';
    }

    if (type === 'Recovery' || type === 'Medical') {
      return 'recovery';
    }

    return 'academy';
  }

  protected formatEventDate(dateValue: string): string {
    return this.cardDateFormatter.format(this.parseIsoDate(dateValue));
  }

  protected get hasSearchTerm(): boolean {
    return this.searchTerm.trim().length > 0;
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

  private parseIsoDate(value: string): Date {
    const [year, month, day] = value.split('-').map((part) => Number(part));
    return new Date(year, month - 1, day);
  }
}
