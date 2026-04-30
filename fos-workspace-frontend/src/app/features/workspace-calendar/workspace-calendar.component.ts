import { DatePipe } from '@angular/common';
import { Component, ElementRef, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { WorkspaceRailComponent } from '../../core/layout/workspace-rail/workspace-rail.component';
import { EventParticipant, ParticipantGroup } from '../../shared/models/event.model';
import { WorkspaceCalendarIconComponent } from '../../shared/workspace-icon/workspace-icon.component';
import {
  WorkspaceCalendarApiAttendee,
  WorkspaceCalendarApiEvent,
  WorkspaceCalendarApiEventType,
  WorkspaceCalendarApiService,
  WorkspaceCalendarCreateEventRequest
} from './workspace-calendar-api.service';

type CalendarViewMode = 'day' | 'week' | 'month' | 'year';
type CalendarRole = 'head-coach' | 'staff';
type AccessScope = 'All Staff' | 'Coaches Only' | 'Medical + Coaches';
type ToastTone = 'success' | 'error' | 'info';

interface CalendarPerson extends EventParticipant {
  canonicalType: 'PLAYER' | 'CLUB';
}

interface CalendarRequiredDocumentDraft {
  id: string;
  name: string;
  assignedToActorId: string;
}

interface CalendarTaskDraft {
  id: string;
  title: string;
  description: string;
  assignedToActorId: string;
  dueAt: string;
}

interface CalendarRequiredDocumentView {
  id: string;
  name: string;
  category: string;
  assignedToActorId: string | null;
  assignedName: string;
  uploaded: boolean;
  submittedDocumentId: string | null;
}

interface CalendarTaskView {
  id: string;
  title: string;
  description: string;
  assignedToActorId: string | null;
  assignedName: string;
  dueAt: string | null;
  completed: boolean;
}

interface CalendarEventView {
  id: string;
  title: string;
  description: string;
  type: WorkspaceCalendarApiEventType;
  startAt: string;
  endAt: string;
  start: Date;
  end: Date;
  location: string;
  coachName: string;
  attendees: CalendarPerson[];
  requiredDocuments: CalendarRequiredDocumentView[];
  tasks: CalendarTaskView[];
  missingDocumentCount: number;
}

interface CalendarDayCell {
  date: Date;
  inCurrentMonth: boolean;
  events: CalendarEventView[];
}

interface YearMonthCard {
  monthDate: Date;
  label: string;
  days: CalendarDayCell[];
  eventCount: number;
}

interface DocumentMenuState {
  x: number;
  y: number;
  document: CalendarRequiredDocumentView;
}

const CURRENT_USER_ID = '11111111-1111-1111-1111-111111111101';

const CALENDAR_PEOPLE: CalendarPerson[] = [
  {
    id: CURRENT_USER_ID,
    name: 'Anton Varga',
    role: 'Head Coach',
    group: 'Staff',
    canonicalType: 'CLUB',
    avatarUrl: ''
  },
  {
    id: '11111111-1111-1111-1111-111111111102',
    name: 'Liam Osei',
    role: 'Performance Analyst',
    group: 'Staff',
    canonicalType: 'CLUB',
    avatarUrl: ''
  },
  {
    id: '11111111-1111-1111-1111-111111111103',
    name: 'Nadia Rahal',
    role: 'Assistant Coach',
    group: 'Staff',
    canonicalType: 'CLUB',
    avatarUrl: ''
  },
  {
    id: '22222222-2222-2222-2222-222222222201',
    name: 'Dr. Helena Ruiz',
    role: 'Physio Lead',
    group: 'Medical Staff',
    canonicalType: 'CLUB',
    avatarUrl: ''
  },
  {
    id: '22222222-2222-2222-2222-222222222202',
    name: 'Aiden Morse',
    role: 'Rehab Specialist',
    group: 'Medical Staff',
    canonicalType: 'CLUB',
    avatarUrl: ''
  },
  {
    id: '33333333-3333-3333-3333-333333333301',
    name: 'Sara Bennett',
    role: 'Operations Manager',
    group: 'Admin Staff',
    canonicalType: 'CLUB',
    avatarUrl: ''
  },
  {
    id: '44444444-4444-4444-4444-444444444401',
    name: 'Leo Carter',
    role: 'Goalkeeper',
    group: 'Player',
    canonicalType: 'PLAYER',
    avatarUrl: ''
  },
  {
    id: '44444444-4444-4444-4444-444444444402',
    name: 'Marco Silva',
    role: 'Center Back',
    group: 'Player',
    canonicalType: 'PLAYER',
    avatarUrl: ''
  },
  {
    id: '44444444-4444-4444-4444-444444444403',
    name: 'Daniel Park',
    role: 'Midfielder',
    group: 'Player',
    canonicalType: 'PLAYER',
    avatarUrl: ''
  }
];

@Component({
  selector: 'app-workspace-calendar',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink, WorkspaceRailComponent, WorkspaceCalendarIconComponent],
  templateUrl: './workspace-calendar.component.html',
  styleUrl: './workspace-calendar.component.scss'
})
export class WorkspaceCalendarComponent {
  @ViewChild('createDrawer') private createDrawer?: ElementRef<HTMLElement>;
  @ViewChild('eventDrawer') private eventDrawer?: ElementRef<HTMLElement>;
  @ViewChild('fileMenu') private fileMenu?: ElementRef<HTMLElement>;

  private readonly calendarApi = inject(WorkspaceCalendarApiService);
  private toastTimer: number | null = null;
  private lastFocusTrigger: HTMLElement | null = null;
  private lastMenuTrigger: HTMLElement | null = null;

  protected readonly mobileNavItems = [
    { label: 'Calendar', route: '/workspace/calendar', icon: 'calendar' },
    { label: 'Documents', route: '/documents', icon: 'folder' },
    { label: 'Players', route: '/players', icon: 'users' },
    { label: 'Settings', route: '/settings', icon: 'settings' }
  ];
  protected readonly viewModes: CalendarViewMode[] = ['day', 'week', 'month', 'year'];
  protected readonly attendeeFilters: Array<'All' | ParticipantGroup> = ['All', 'Player', 'Staff', 'Medical Staff', 'Admin Staff'];
  protected readonly accessOptions: AccessScope[] = ['All Staff', 'Coaches Only', 'Medical + Coaches'];
  protected readonly eventTypes: Array<{ value: WorkspaceCalendarApiEventType; label: string }> = [
    { value: 'TRAINING', label: 'Training' },
    { value: 'MATCH', label: 'Match' },
    { value: 'MEETING', label: 'Meeting' },
    { value: 'MEDICAL_CHECK', label: 'Medical' },
    { value: 'ADMINISTRATIVE', label: 'Administrative' },
    { value: 'OTHER', label: 'Other' }
  ];
  protected readonly hours = Array.from({ length: 24 }, (_, index) => index);
  protected readonly peopleDirectory = CALENDAR_PEOPLE;
  protected readonly currentUser = CALENDAR_PEOPLE.find((person) => person.id === CURRENT_USER_ID) ?? CALENDAR_PEOPLE[0];
  protected readonly currentRole: CalendarRole = 'head-coach';

  protected viewMode: CalendarViewMode = 'month';
  protected visibleDate = this.startOfDay(new Date());
  protected selectedDate = this.startOfDay(new Date());
  protected activeDate = this.startOfDay(new Date());
  protected searchTerm = '';
  protected leftRailOpen = false;
  protected isLoading = true;
  protected loadError = '';
  protected events: CalendarEventView[] = [];

  protected selectedEvent: CalendarEventView | null = null;
  protected drawerOpen = false;
  protected noteDraft = '';
  protected documentMenu: DocumentMenuState | null = null;
  protected createDrawerOpen = false;

  protected toastMessage = '';
  protected toastTone: ToastTone = 'info';

  protected draftEventName = '';
  protected draftDescription = '';
  protected draftLocation = '';
  protected draftAccess: AccessScope = 'All Staff';
  protected draftEventType: WorkspaceCalendarApiEventType = 'TRAINING';
  protected draftDateValue = this.toDateInputValue(new Date());
  protected draftStartTime = '09:00';
  protected draftEndTime = '10:30';
  protected attendeeSearch = '';
  protected attendeeFilter: 'All' | ParticipantGroup = 'All';
  protected selectedAttendeeIds: string[] = [CURRENT_USER_ID];
  protected requiredDocumentDrafts: CalendarRequiredDocumentDraft[] = [this.newRequiredDocumentDraft()];
  protected taskTitle = '';
  protected taskDescription = '';
  protected taskAssigneeId = CURRENT_USER_ID;
  protected taskDueAt = '';
  protected taskDrafts: CalendarTaskDraft[] = [];

  constructor() {
    this.loadEvents();
  }

  protected get canManageEvents(): boolean {
    return this.currentRole === 'head-coach';
  }

  protected get pageTitle(): string {
    if (this.viewMode === 'day') {
      return new Intl.DateTimeFormat('en-US', {
        weekday: 'long',
        month: 'long',
        day: 'numeric',
        year: 'numeric'
      }).format(this.visibleDate);
    }

    if (this.viewMode === 'week') {
      const weekDays = this.weekDates;
      const start = weekDays[0];
      const end = weekDays[6];
      return `${this.shortDateLabel(start)} - ${this.shortDateLabel(end)}`;
    }

    if (this.viewMode === 'year') {
      return String(this.visibleDate.getFullYear());
    }

    return new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' }).format(this.visibleDate);
  }

  protected get filteredParticipants(): CalendarPerson[] {
    const query = this.attendeeSearch.trim().toLowerCase();

    return this.peopleDirectory
      .filter((person) => this.attendeeFilter === 'All' || person.group === this.attendeeFilter)
      .filter((person) => {
        if (!query) {
          return true;
        }

        return (
          person.name.toLowerCase().includes(query) ||
          person.role.toLowerCase().includes(query) ||
          person.group.toLowerCase().includes(query)
        );
      });
  }

  protected get selectedAttendees(): CalendarPerson[] {
    return this.selectedAttendeeIds
      .map((participantId) => this.personById(participantId))
      .filter((participant): participant is CalendarPerson => participant !== null);
  }

  protected get visibleEvents(): CalendarEventView[] {
    const query = this.searchTerm.trim().toLowerCase();

    if (!query) {
      return this.events;
    }

    return this.events.filter((event) => {
      const attendeeMatch = event.attendees.some((attendee) => attendee.name.toLowerCase().includes(query));
      const documentMatch = event.requiredDocuments.some((document) => document.name.toLowerCase().includes(query));
      const taskMatch = event.tasks.some((task) => task.title.toLowerCase().includes(query) || task.description.toLowerCase().includes(query));

      return (
        event.title.toLowerCase().includes(query) ||
        event.description.toLowerCase().includes(query) ||
        event.location.toLowerCase().includes(query) ||
        attendeeMatch ||
        documentMatch ||
        taskMatch
      );
    });
  }

  protected get weekDates(): Date[] {
    const start = this.startOfWeek(this.visibleDate);
    return Array.from({ length: 7 }, (_, index) => this.addDays(start, index));
  }

  protected get monthCells(): CalendarDayCell[] {
    return this.buildMonthCells(this.visibleDate);
  }

  protected get miniMonthCells(): CalendarDayCell[] {
    return this.buildMonthCells(this.visibleDate);
  }

  protected get yearMonthCards(): YearMonthCard[] {
    const year = this.visibleDate.getFullYear();

    return Array.from({ length: 12 }, (_, monthIndex) => {
      const monthDate = new Date(year, monthIndex, 1);
      const days = this.buildMonthCells(monthDate);
      const eventCount = this.visibleEvents.filter((event) => event.start.getFullYear() === year && event.start.getMonth() === monthIndex).length;

      return {
        monthDate,
        label: new Intl.DateTimeFormat('en-US', { month: 'short' }).format(monthDate),
        days,
        eventCount
      };
    });
  }

  protected get leftRailMissingDocuments(): Array<{ event: CalendarEventView; document: CalendarRequiredDocumentView }> {
    return this.visibleEvents
      .flatMap((event) => event.requiredDocuments.filter((document) => !document.uploaded).map((document) => ({ event, document })))
      .slice(0, 6);
  }

  protected get leftRailTasks(): Array<{ event: CalendarEventView; task: CalendarTaskView }> {
    return this.visibleEvents.flatMap((event) => event.tasks.map((task) => ({ event, task }))).slice(0, 6);
  }

  protected get selectedDateEvents(): CalendarEventView[] {
    return this.eventsForDate(this.selectedDate);
  }

  protected get selectedDateMissingCount(): number {
    return this.selectedDateEvents.reduce((count, event) => count + event.missingDocumentCount, 0);
  }

  protected get createDisabled(): boolean {
    return !this.draftEventName.trim();
  }

  protected toggleLeftRail(): void {
    this.leftRailOpen = !this.leftRailOpen;
  }

  protected retryLoad(): void {
    this.loadEvents();
  }

  protected closeLeftRail(): void {
    this.leftRailOpen = false;
  }

  protected handleMonthCellKeydown(event: KeyboardEvent, cell: CalendarDayCell): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.chooseMonthCell(cell, event);
      return;
    }

    this.onDateKeydown(event, cell.date, 'day');
  }

  protected setViewMode(nextView: CalendarViewMode): void {
    this.viewMode = nextView;
  }

  protected goToToday(): void {
    const today = this.startOfDay(new Date());
    this.visibleDate = today;
    this.selectedDate = today;
    this.activeDate = today;
  }

  protected goPrevious(): void {
    if (this.viewMode === 'day') {
      this.visibleDate = this.addDays(this.visibleDate, -1);
    } else if (this.viewMode === 'week') {
      this.visibleDate = this.addDays(this.visibleDate, -7);
    } else if (this.viewMode === 'year') {
      this.visibleDate = new Date(this.visibleDate.getFullYear() - 1, 0, 1);
    } else {
      this.visibleDate = new Date(this.visibleDate.getFullYear(), this.visibleDate.getMonth() - 1, 1);
    }

    this.selectedDate = this.startOfDay(this.visibleDate);
    this.activeDate = this.startOfDay(this.visibleDate);
  }

  protected goNext(): void {
    if (this.viewMode === 'day') {
      this.visibleDate = this.addDays(this.visibleDate, 1);
    } else if (this.viewMode === 'week') {
      this.visibleDate = this.addDays(this.visibleDate, 7);
    } else if (this.viewMode === 'year') {
      this.visibleDate = new Date(this.visibleDate.getFullYear() + 1, 0, 1);
    } else {
      this.visibleDate = new Date(this.visibleDate.getFullYear(), this.visibleDate.getMonth() + 1, 1);
    }

    this.selectedDate = this.startOfDay(this.visibleDate);
    this.activeDate = this.startOfDay(this.visibleDate);
  }

  protected selectMiniMonthDate(date: Date): void {
    this.visibleDate = this.startOfDay(date);
    this.selectedDate = this.startOfDay(date);
    this.activeDate = this.startOfDay(date);

    if (this.viewMode === 'year') {
      this.viewMode = 'month';
    }
  }

  protected chooseMonthCell(cell: CalendarDayCell, event: Event): void {
    this.selectedDate = this.startOfDay(cell.date);
    this.activeDate = this.startOfDay(cell.date);

    if (cell.events.length === 0) {
      this.openCreateDrawer(cell.date, 9, event.currentTarget);
    }
  }

  protected chooseTimeSlot(date: Date, hour: number, event: Event): void {
    this.selectedDate = this.startOfDay(date);
    this.activeDate = this.startOfDay(date);
    this.openCreateDrawer(date, hour, event.currentTarget);
  }

  protected closeCreateDrawer(): void {
    this.createDrawerOpen = false;
    this.restoreFocus();
  }

  protected toggleAttendee(participantId: string): void {
    if (this.selectedAttendeeIds.includes(participantId)) {
      this.selectedAttendeeIds = this.selectedAttendeeIds.filter((id) => id !== participantId);
      return;
    }

    this.selectedAttendeeIds = [...this.selectedAttendeeIds, participantId];
  }

  protected removeAttendee(participantId: string): void {
    this.selectedAttendeeIds = this.selectedAttendeeIds.filter((id) => id !== participantId);
  }

  protected isAttendeeSelected(participantId: string): boolean {
    return this.selectedAttendeeIds.includes(participantId);
  }

  protected addRequiredDocumentRow(): void {
    this.requiredDocumentDrafts = [...this.requiredDocumentDrafts, this.newRequiredDocumentDraft()];
  }

  protected removeRequiredDocumentRow(rowId: string): void {
    if (this.requiredDocumentDrafts.length === 1) {
      this.requiredDocumentDrafts = [this.newRequiredDocumentDraft()];
      return;
    }

    this.requiredDocumentDrafts = this.requiredDocumentDrafts.filter((row) => row.id !== rowId);
  }

  protected addTask(): void {
    if (!this.taskTitle.trim()) {
      return;
    }

    this.taskDrafts = [
      ...this.taskDrafts,
      {
        id: this.newId('task'),
        title: this.taskTitle.trim(),
        description: this.taskDescription.trim(),
        assignedToActorId: this.taskAssigneeId,
        dueAt: this.taskDueAt
      }
    ];

    this.taskTitle = '';
    this.taskDescription = '';
    this.taskDueAt = '';
  }

  protected removeTask(taskId: string): void {
    this.taskDrafts = this.taskDrafts.filter((task) => task.id !== taskId);
  }

  protected createEvent(): void {
    if (this.createDisabled) {
      return;
    }

    const startAt = this.combineDateAndTime(this.draftDateValue, this.draftStartTime);
    const endAt = this.combineDateAndTime(this.draftDateValue, this.draftEndTime);

    if (Date.parse(endAt) <= Date.parse(startAt)) {
      this.showToast('End time must be after start time.', 'error');
      return;
    }

    const request: WorkspaceCalendarCreateEventRequest = {
      title: this.draftEventName.trim(),
      description: `[Access: ${this.draftAccess}]${this.draftDescription.trim() ? ` ${this.draftDescription.trim()}` : ''}`,
      type: this.draftEventType,
      startAt,
      endAt,
      location: this.draftLocation.trim() || null,
      attendees: this.selectedAttendees.map((attendee) => ({
        actorId: attendee.id,
        mandatory: true,
        canonicalType: attendee.canonicalType
      })),
      requiredDocuments: this.requiredDocumentDrafts
        .filter((document) => document.name.trim())
        .map((document) => ({
          description: document.name.trim(),
          documentCategory: 'GENERAL',
          assignedToActorId: document.assignedToActorId || null
        })),
      tasks: this.taskDrafts.map((task) => ({
        title: task.title,
        description: task.description || task.title,
        assignedToActorId: task.assignedToActorId || null,
        dueAt: task.dueAt ? new Date(task.dueAt).toISOString() : null
      }))
    };

    this.calendarApi.createEvent(request).subscribe({
      next: (eventResponse) => {
        this.closeCreateDrawer();
        this.showToast('Event created', 'success');
        this.loadEvents(eventResponse.eventId);
      },
      error: () => {
        this.showToast('Unable to create event. Check that the gateway is running on http://localhost:8080.', 'error');
      }
    });
  }

  protected openEvent(eventItem: CalendarEventView, trigger?: EventTarget | null): void {
    this.selectedEvent = eventItem;
    this.selectedDate = this.startOfDay(eventItem.start);
    this.activeDate = this.startOfDay(eventItem.start);
    this.drawerOpen = true;
    this.documentMenu = null;

    if (trigger instanceof HTMLElement) {
      this.rememberTrigger(trigger);
    }

    window.setTimeout(() => this.focusOverlay(this.eventDrawer?.nativeElement), 0);
  }

  protected closeDrawer(): void {
    this.drawerOpen = false;
    this.selectedEvent = null;
    this.documentMenu = null;
    this.lastMenuTrigger = null;
    this.restoreFocus();
  }

  protected deleteSelectedEvent(): void {
    if (!this.selectedEvent || !this.canManageEvents) {
      return;
    }

    const confirmed = window.confirm(`Delete "${this.selectedEvent.title}"?`);
    if (!confirmed) {
      return;
    }

    const eventId = this.selectedEvent.id;
    this.calendarApi.deleteEvent(eventId).subscribe({
      next: () => {
        this.closeDrawer();
        this.showToast('Event deleted', 'success');
        this.loadEvents();
      },
      error: () => {
        this.showToast('Unable to delete the event.', 'error');
      }
    });
  }

  protected submitNote(): void {
    if (!this.noteDraft.trim()) {
      return;
    }

    this.noteDraft = '';
    this.showToast('Note saved for this session.', 'info');
  }

  protected uploadDocument(): void {
    this.showToast('Use the Documents module to upload files for event requirements.', 'info');
  }

  protected openDocumentMenu(mouseEvent: MouseEvent, document: CalendarRequiredDocumentView): void {
    mouseEvent.preventDefault();
    mouseEvent.stopPropagation();

    if (mouseEvent.currentTarget instanceof HTMLElement) {
      this.lastMenuTrigger = mouseEvent.currentTarget;
    }

    this.documentMenu = {
      x: Math.min(mouseEvent.clientX, window.innerWidth - 188),
      y: Math.min(mouseEvent.clientY, window.innerHeight - 220),
      document
    };

    window.setTimeout(() => this.focusOverlay(this.fileMenu?.nativeElement), 0);
  }

  protected closeDocumentMenu(): void {
    this.documentMenu = null;
    this.restoreMenuFocus();
  }

  protected openDocumentMenuFromTrigger(document: CalendarRequiredDocumentView, trigger: EventTarget | null): void {
    if (!(trigger instanceof HTMLElement)) {
      return;
    }

    const rect = trigger.getBoundingClientRect();
    this.lastMenuTrigger = trigger;
    this.documentMenu = {
      x: Math.max(8, Math.min(rect.right - 180, window.innerWidth - 188)),
      y: Math.max(8, Math.min(rect.bottom + 4, window.innerHeight - 220)),
      document
    };

    window.setTimeout(() => this.focusOverlay(this.fileMenu?.nativeElement), 0);
  }

  protected handleDocumentMenuKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeDocumentMenu();
      return;
    }

    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
      return;
    }

    const items = this.focusableElements(event.currentTarget as HTMLElement);
    if (items.length === 0) {
      return;
    }

    const activeIndex = Math.max(
      0,
      items.findIndex((item) => item === document.activeElement)
    );

    event.preventDefault();

    if (event.key === 'Home') {
      items[0].focus();
      return;
    }

    if (event.key === 'End') {
      items[items.length - 1].focus();
      return;
    }

    const offset = event.key === 'ArrowDown' ? 1 : -1;
    const nextIndex = (activeIndex + offset + items.length) % items.length;
    items[nextIndex].focus();
  }

  protected handleDocumentMenuAction(action: 'open' | 'download' | 'share' | 'rename' | 'delete'): void {
    const document = this.documentMenu?.document;
    this.closeDocumentMenu();

    if (!document) {
      return;
    }

    if (!document.uploaded) {
      this.showToast(`${document.name} is still marked as missing.`, 'info');
      return;
    }

    if (action === 'delete') {
      this.showToast('Document unlinking is not connected to the event API yet.', 'info');
      return;
    }

    this.showToast(`The ${action} action will be wired when event-linked files expose a download endpoint.`, 'info');
  }

  protected closeToast(): void {
    this.toastMessage = '';
  }

  protected viewButtonLabel(viewMode: CalendarViewMode): string {
    return viewMode.charAt(0).toUpperCase() + viewMode.slice(1);
  }

  protected eventsForGridDate(date: Date): CalendarEventView[] {
    return this.eventsForDate(date).slice(0, 2);
  }

  protected overflowCount(date: Date): number {
    return Math.max(0, this.eventsForDate(date).length - 2);
  }

  protected eventsForWeekDate(date: Date): CalendarEventView[] {
    return this.eventsForDate(date);
  }

  protected eventTone(eventType: WorkspaceCalendarApiEventType): 'training' | 'match' | 'meeting' | 'medical' | 'other' {
    if (eventType === 'TRAINING') {
      return 'training';
    }

    if (eventType === 'MATCH') {
      return 'match';
    }

    if (eventType === 'MEETING') {
      return 'meeting';
    }

    if (eventType === 'MEDICAL_CHECK') {
      return 'medical';
    }

    return 'other';
  }

  protected eventTypeLabel(eventType: WorkspaceCalendarApiEventType): string {
    return this.eventTypes.find((type) => type.value === eventType)?.label ?? 'Other';
  }

  protected eventTimeLabel(eventItem: CalendarEventView): string {
    return `${this.shortTimeLabel(eventItem.start)} - ${this.shortTimeLabel(eventItem.end)}`;
  }

  protected eventTimeRangeAriaLabel(eventItem: CalendarEventView): string {
    return `${this.shortTimeLabel(eventItem.start)}, ${eventItem.missingDocumentCount} missing documents`;
  }

  protected weekEventTop(eventItem: CalendarEventView): number {
    return this.minutesFromMidnight(eventItem.start) * 1.2;
  }

  protected weekEventHeight(eventItem: CalendarEventView): number {
    const durationMinutes = Math.max(30, (eventItem.end.getTime() - eventItem.start.getTime()) / 60000);
    return Math.max(48, durationMinutes * 1.2);
  }

  protected monthCellAriaLabel(cell: CalendarDayCell): string {
    const eventCount = this.eventsForDate(cell.date).length;
    return `${new Intl.DateTimeFormat('en-US', { month: 'long', day: 'numeric', year: 'numeric' }).format(cell.date)}, ${eventCount} events`;
  }

  protected isToday(date: Date): boolean {
    return this.sameDay(date, new Date());
  }

  protected isSelected(date: Date): boolean {
    return this.sameDay(date, this.selectedDate);
  }

  protected isActiveDate(date: Date): boolean {
    return this.sameDay(date, this.activeDate);
  }

  protected onDateKeydown(event: KeyboardEvent, date: Date, unit: 'day' | 'week' | 'month-card'): void {
    let nextDate: Date | null = null;

    if (event.key === 'ArrowRight') {
      nextDate = this.addDays(date, 1);
    } else if (event.key === 'ArrowLeft') {
      nextDate = this.addDays(date, -1);
    } else if (event.key === 'ArrowDown') {
      nextDate = unit === 'month-card' ? new Date(date.getFullYear(), date.getMonth() + 4, 1) : this.addDays(date, 7);
    } else if (event.key === 'ArrowUp') {
      nextDate = unit === 'month-card' ? new Date(date.getFullYear(), date.getMonth() - 4, 1) : this.addDays(date, -7);
    } else if (event.key === 'Home') {
      if (unit === 'month-card') {
        nextDate = new Date(date.getFullYear(), Math.floor(date.getMonth() / 4) * 4, 1);
      } else {
        nextDate = this.startOfWeek(date);
      }
    } else if (event.key === 'End') {
      if (unit === 'month-card') {
        nextDate = new Date(date.getFullYear(), Math.floor(date.getMonth() / 4) * 4 + 3, 1);
      } else {
        nextDate = this.addDays(this.startOfWeek(date), 6);
      }
    }

    if (!nextDate) {
      return;
    }

    event.preventDefault();
    this.activeDate = this.startOfDay(nextDate);
    this.selectedDate = this.startOfDay(nextDate);

    if (this.viewMode === 'month') {
      this.visibleDate = new Date(this.activeDate.getFullYear(), this.activeDate.getMonth(), 1);
    }

    if (this.viewMode === 'year') {
      this.visibleDate = new Date(this.activeDate.getFullYear(), 0, 1);
    }

    window.setTimeout(() => {
      const target = document.querySelector<HTMLElement>(`[data-date-key="${this.toDateKey(this.activeDate)}"]`);
      target?.focus();
    }, 0);
  }

  protected handleOverlayKeydown(event: KeyboardEvent): void {
    const container = event.currentTarget as HTMLElement;

    if (event.key === 'Escape') {
      event.preventDefault();
      if (this.createDrawerOpen) {
        this.closeCreateDrawer();
      } else if (this.drawerOpen) {
        this.closeDrawer();
      }
      return;
    }

    if (event.key !== 'Tab') {
      return;
    }

    const focusable = this.focusableElements(container);
    if (focusable.length === 0) {
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement as HTMLElement | null;

    if (event.shiftKey && active === first) {
      event.preventDefault();
      last.focus();
    }

    if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }

  protected eventCardLabel(eventItem: CalendarEventView): string {
    return `${eventItem.title}, ${this.eventTypeLabel(eventItem.type)}, ${this.shortTimeLabel(eventItem.start)}, ${eventItem.missingDocumentCount} missing documents`;
  }

  protected monthCardOpen(monthDate: Date): void {
    this.viewMode = 'month';
    this.visibleDate = new Date(monthDate.getFullYear(), monthDate.getMonth(), 1);
    this.selectedDate = this.visibleDate;
    this.activeDate = this.visibleDate;
  }

  protected dateKey(date: Date): string {
    return this.toDateKey(date);
  }

  protected shortDateLabel(date: Date): string {
    return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric' }).format(date);
  }

  protected shortWeekdayLabel(date: Date): string {
    return new Intl.DateTimeFormat('en-US', { weekday: 'short' }).format(date);
  }

  protected shortTimeLabel(date: Date): string {
    return new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' }).format(date);
  }

  protected hourLabel(hour: number): string {
    return new Intl.DateTimeFormat('en-US', { hour: 'numeric' }).format(new Date(2026, 0, 1, hour));
  }

  protected personById(participantId: string): CalendarPerson | null {
    return this.peopleDirectory.find((participant) => participant.id === participantId) ?? null;
  }

  protected initialsFor(name: string): string {
    const parts = name
      .trim()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2);

    return parts.map((part) => part.charAt(0).toUpperCase()).join('') || '?';
  }

  private loadEvents(focusEventId?: string): void {
    this.isLoading = true;
    this.loadError = '';

    this.calendarApi.listEvents().subscribe({
      next: (eventResponses) => {
        this.events = eventResponses.map((eventResponse) => this.mapApiEvent(eventResponse));
        this.isLoading = false;

        if (focusEventId) {
          const focusedEvent = this.events.find((event) => event.id === focusEventId) ?? null;
          if (focusedEvent) {
            this.openEvent(focusedEvent);
          }
          return;
        }

        if (this.selectedEvent) {
          this.selectedEvent = this.events.find((event) => event.id === this.selectedEvent?.id) ?? null;
          this.drawerOpen = this.selectedEvent !== null;
        }
      },
      error: () => {
        this.isLoading = false;
        this.loadError = 'Unable to load calendar events from the gateway. Start the gateway on http://localhost:8080 and try again.';
      }
    });
  }

  private mapApiEvent(eventResponse: WorkspaceCalendarApiEvent): CalendarEventView {
    const start = new Date(eventResponse.startAt);
    const end = new Date(eventResponse.endAt);
    const attendees = this.resolveAttendees(eventResponse.attendees);

    const requiredDocuments = (eventResponse.requiredDocuments ?? []).map((document) => ({
      id: document.requirementId,
      name: document.description,
      category: document.documentCategory,
      assignedToActorId: document.assignedToActorId,
      assignedName: this.resolvePersonName(document.assignedToActorId),
      uploaded: document.submitted,
      submittedDocumentId: document.submittedDocumentId
    }));

    const tasks = (eventResponse.tasks ?? []).map((task) => ({
      id: task.taskId,
      title: task.title,
      description: task.description,
      assignedToActorId: task.assignedToActorId,
      assignedName: this.resolvePersonName(task.assignedToActorId),
      dueAt: task.dueAt,
      completed: task.completed
    }));

    const coachName = attendees.find((person) => person.role === 'Head Coach')?.name ?? this.currentUser.name;

    return {
      id: eventResponse.eventId,
      title: eventResponse.title,
      description: eventResponse.description ?? 'No event description provided.',
      type: eventResponse.type,
      startAt: eventResponse.startAt,
      endAt: eventResponse.endAt,
      start,
      end,
      location: eventResponse.location ?? 'No location assigned',
      coachName,
      attendees,
      requiredDocuments,
      tasks,
      missingDocumentCount: requiredDocuments.filter((document) => !document.uploaded).length
    };
  }

  private resolveAttendees(attendees: WorkspaceCalendarApiAttendee[]): CalendarPerson[] {
    return attendees
      .map((attendee) => this.personByCanonicalRef(attendee))
      .filter((participant): participant is CalendarPerson => participant !== null);
  }

  private personByCanonicalRef(attendee: WorkspaceCalendarApiAttendee): CalendarPerson | null {
    return this.peopleDirectory.find((person) => person.id === attendee.canonicalRef.id) ?? null;
  }

  private resolvePersonName(actorId: string | null): string {
    if (!actorId) {
      return 'Unassigned';
    }

    return this.personById(actorId)?.name ?? 'Unassigned';
  }

  private eventsForDate(date: Date): CalendarEventView[] {
    return this.visibleEvents.filter((event) => this.sameDay(event.start, date)).sort((left, right) => left.start.getTime() - right.start.getTime());
  }

  private buildMonthCells(anchorDate: Date): CalendarDayCell[] {
    const monthStart = new Date(anchorDate.getFullYear(), anchorDate.getMonth(), 1);
    const firstDayIndex = (monthStart.getDay() + 6) % 7;
    const gridStart = this.addDays(monthStart, -firstDayIndex);

    return Array.from({ length: 42 }, (_, index) => {
      const date = this.addDays(gridStart, index);

      return {
        date,
        inCurrentMonth: date.getMonth() === monthStart.getMonth(),
        events: this.eventsForDate(date)
      };
    });
  }

  private openCreateDrawer(date: Date, hour: number, trigger?: EventTarget | null): void {
    if (!this.canManageEvents) {
      return;
    }

    this.resetCreateForm(this.startOfDay(date), hour);
    this.selectedEvent = null;
    this.drawerOpen = false;
    this.documentMenu = null;
    this.leftRailOpen = false;
    this.createDrawerOpen = true;

    if (trigger instanceof HTMLElement) {
      this.rememberTrigger(trigger);
    }

    window.setTimeout(() => this.focusOverlay(this.createDrawer?.nativeElement), 0);
  }

  private resetCreateForm(date: Date, hour: number): void {
    const endHour = Math.min(hour + 1, 23);

    this.draftEventName = '';
    this.draftDescription = '';
    this.draftLocation = '';
    this.draftAccess = 'All Staff';
    this.draftEventType = 'TRAINING';
    this.draftDateValue = this.toDateInputValue(date);
    this.draftStartTime = `${String(hour).padStart(2, '0')}:00`;
    this.draftEndTime = `${String(endHour).padStart(2, '0')}:30`;
    this.attendeeSearch = '';
    this.attendeeFilter = 'All';
    this.selectedAttendeeIds = [CURRENT_USER_ID];
    this.requiredDocumentDrafts = [this.newRequiredDocumentDraft()];
    this.taskTitle = '';
    this.taskDescription = '';
    this.taskAssigneeId = CURRENT_USER_ID;
    this.taskDueAt = '';
    this.taskDrafts = [];
  }

  private rememberTrigger(trigger: HTMLElement): void {
    this.lastFocusTrigger = trigger;
  }

  private restoreFocus(): void {
    this.lastFocusTrigger?.focus();
    this.lastFocusTrigger = null;
  }

  private restoreMenuFocus(): void {
    this.lastMenuTrigger?.focus();
    this.lastMenuTrigger = null;
  }

  private focusOverlay(container: HTMLElement | undefined): void {
    if (!container) {
      return;
    }

    const firstFocusable = this.focusableElements(container)[0] ?? container;
    firstFocusable.focus();
  }

  private focusableElements(container: HTMLElement): HTMLElement[] {
    return Array.from(
      container.querySelectorAll<HTMLElement>(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )
    );
  }

  private showToast(message: string, tone: ToastTone): void {
    this.toastMessage = message;
    this.toastTone = tone;

    if (this.toastTimer) {
      window.clearTimeout(this.toastTimer);
    }

    this.toastTimer = window.setTimeout(() => {
      this.toastMessage = '';
      this.toastTimer = null;
    }, 3600);
  }

  private sameDay(left: Date, right: Date): boolean {
    return this.toDateKey(left) === this.toDateKey(right);
  }

  private startOfDay(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate());
  }

  private startOfWeek(date: Date): Date {
    const normalized = this.startOfDay(date);
    return this.addDays(normalized, -((normalized.getDay() + 6) % 7));
  }

  private addDays(date: Date, amount: number): Date {
    const nextDate = new Date(date);
    nextDate.setDate(nextDate.getDate() + amount);
    return this.startOfDay(nextDate);
  }

  private minutesFromMidnight(date: Date): number {
    return date.getHours() * 60 + date.getMinutes();
  }

  private toDateKey(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  private combineDateAndTime(dateValue: string, timeValue: string): string {
    const localDate = new Date(`${dateValue}T${timeValue}:00`);
    return localDate.toISOString();
  }

  private toDateInputValue(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  private newRequiredDocumentDraft(): CalendarRequiredDocumentDraft {
    return {
      id: this.newId('doc'),
      name: '',
      assignedToActorId: ''
    };
  }

  private newId(prefix: string): string {
    return `${prefix}-${Math.random().toString(36).slice(2, 9)}`;
  }
}
