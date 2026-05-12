import { DatePipe } from '@angular/common';
import { Component, ElementRef, HostListener, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { EventParticipant } from '../../shared/models/event.model';
import { WorkspaceCalendarIconComponent } from '../../shared/workspace-icon/workspace-icon.component';
import {
  WorkspaceCalendarApiAttendee,
  WorkspaceCalendarApiEvent,
  WorkspaceCalendarApiEventType,
  WorkspaceCalendarApiService,
  WorkspaceCalendarCreateEventRequest,
  WorkspaceCalendarUpdateEventRequest
} from './workspace-calendar-api.service';

type CalendarViewMode = 'day' | 'week' | 'month' | 'year';
type CalendarViewSelectorMode = Extract<CalendarViewMode, 'day' | 'week' | 'month'>;
type CalendarRole = 'head-coach' | 'staff';
type ToastTone = 'success' | 'error' | 'info';

interface CalendarPerson extends EventParticipant {
  canonicalType: 'PLAYER' | 'CLUB';
  email: string;
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

interface CalendarEventView {
  id: string;
  createdByActorId: string | null;
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

interface CreatePopoverState {
  x: number;
  y: number;
  date: Date;
  hour: number;
}

interface CalendarViewOption {
  mode: CalendarViewSelectorMode;
  label: string;
}

const CURRENT_USER_ID = '11111111-1111-1111-1111-111111111101';
// TODO(calendar-auth): Remove this temporary frontend-only fallback when local dev has an auth-backed user role source.
// It only controls calendar UI visibility while environment.auth.enabled is false; backend policy remains authoritative.
const TEMP_DISABLED_AUTH_ROLE: CalendarRole = 'head-coach';

const CALENDAR_PEOPLE: CalendarPerson[] = [
  {
    id: CURRENT_USER_ID,
    name: 'Anton Varga',
    email: 'anton.varga@fos.club',
    role: 'Head Coach',
    group: 'Staff',
    canonicalType: 'CLUB',
    avatarUrl: ''
  },
  {
    id: '11111111-1111-1111-1111-111111111102',
    name: 'Liam Osei',
    email: 'liam.osei@fos.club',
    role: 'Performance Analyst',
    group: 'Staff',
    canonicalType: 'CLUB',
    avatarUrl: ''
  },
  {
    id: '11111111-1111-1111-1111-111111111103',
    name: 'Nadia Rahal',
    email: 'nadia.rahal@fos.club',
    role: 'Assistant Coach',
    group: 'Staff',
    canonicalType: 'CLUB',
    avatarUrl: ''
  },
  {
    id: '22222222-2222-2222-2222-222222222201',
    name: 'Dr. Helena Ruiz',
    email: 'helena.ruiz@fos.club',
    role: 'Physio Lead',
    group: 'Medical Staff',
    canonicalType: 'CLUB',
    avatarUrl: ''
  },
  {
    id: '22222222-2222-2222-2222-222222222202',
    name: 'Aiden Morse',
    email: 'aiden.morse@fos.club',
    role: 'Rehab Specialist',
    group: 'Medical Staff',
    canonicalType: 'CLUB',
    avatarUrl: ''
  },
  {
    id: '33333333-3333-3333-3333-333333333301',
    name: 'Sara Bennett',
    email: 'sara.bennett@fos.club',
    role: 'Operations Manager',
    group: 'Admin Staff',
    canonicalType: 'CLUB',
    avatarUrl: ''
  },
  {
    id: '44444444-4444-4444-4444-444444444401',
    name: 'Leo Carter',
    email: 'leo.carter@fos.club',
    role: 'Goalkeeper',
    group: 'Player',
    canonicalType: 'PLAYER',
    avatarUrl: ''
  },
  {
    id: '44444444-4444-4444-4444-444444444402',
    name: 'Marco Silva',
    email: 'marco.silva@fos.club',
    role: 'Center Back',
    group: 'Player',
    canonicalType: 'PLAYER',
    avatarUrl: ''
  },
  {
    id: '44444444-4444-4444-4444-444444444403',
    name: 'Daniel Park',
    email: 'daniel.park@fos.club',
    role: 'Midfielder',
    group: 'Player',
    canonicalType: 'PLAYER',
    avatarUrl: ''
  }
];

@Component({
  selector: 'app-workspace-calendar',
  standalone: true,
  imports: [DatePipe, FormsModule, WorkspaceCalendarIconComponent],
  templateUrl: './workspace-calendar.component.html',
  styleUrl: './workspace-calendar.component.scss'
})
export class WorkspaceCalendarComponent {
  @ViewChild('createDrawer') private createDrawer?: ElementRef<HTMLElement>;
  @ViewChild('eventDrawer') private eventDrawer?: ElementRef<HTMLElement>;
  @ViewChild('monthPicker') private monthPicker?: ElementRef<HTMLElement>;
  @ViewChild('monthPickerPanel') private monthPickerPanel?: ElementRef<HTMLElement>;
  @ViewChild('monthPickerTrigger') private monthPickerTrigger?: ElementRef<HTMLButtonElement>;
  @ViewChild('viewSelector') private viewSelector?: ElementRef<HTMLElement>;
  @ViewChild('viewMenu') private viewMenu?: ElementRef<HTMLElement>;
  @ViewChild('viewMenuTrigger') private viewMenuTrigger?: ElementRef<HTMLButtonElement>;
  @ViewChild('moreOptions') private moreOptions?: ElementRef<HTMLElement>;
  @ViewChild('moreOptionsMenu') private moreOptionsMenu?: ElementRef<HTMLElement>;
  @ViewChild('moreOptionsTrigger') private moreOptionsTrigger?: ElementRef<HTMLButtonElement>;
  @ViewChild('attendeePicker') private attendeePicker?: ElementRef<HTMLElement>;
  @ViewChild('attendeeInput') private attendeeInput?: ElementRef<HTMLInputElement>;

  private readonly authService = environment.auth.enabled ? inject(AuthService) : null;
  private readonly calendarApi = inject(WorkspaceCalendarApiService);
  private toastTimer: number | null = null;
  private lastFocusTrigger: HTMLElement | null = null;

  protected readonly viewOptions: CalendarViewOption[] = [
    { mode: 'day', label: 'Day' },
    { mode: 'week', label: 'Week' },
    { mode: 'month', label: 'Month' }
  ];
  protected readonly eventTypes: Array<{ value: WorkspaceCalendarApiEventType; label: string }> = [
    { value: 'TRAINING', label: 'Training' },
    { value: 'MATCH', label: 'Match' },
    { value: 'MEETING', label: 'Meeting' },
    { value: 'OTHER', label: 'Other' }
  ];
  protected readonly monthPickerLabels = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  protected readonly hours = Array.from({ length: 24 }, (_, index) => index);
  protected readonly peopleDirectory = CALENDAR_PEOPLE;
  protected readonly currentUser = CALENDAR_PEOPLE.find((person) => person.id === CURRENT_USER_ID) ?? CALENDAR_PEOPLE[0];
  protected readonly attendeeInputSupported = true;

  protected viewMode: CalendarViewMode = 'month';
  protected visibleDate = this.startOfDay(new Date());
  protected selectedDate = this.startOfDay(new Date());
  protected activeDate = this.startOfDay(new Date());
  protected miniCalendarDate = new Date(this.visibleDate.getFullYear(), this.visibleDate.getMonth(), 1);
  protected leftRailOpen = false;
  protected monthPickerOpen = false;
  protected monthPickerYear = this.visibleDate.getFullYear();
  protected viewMenuOpen = false;
  protected moreOptionsMenuOpen = false;
  protected isLoading = true;
  protected loadError = '';
  protected events: CalendarEventView[] = [];

  protected selectedEvent: CalendarEventView | null = null;
  protected drawerOpen = false;
  protected drawerError = '';
  protected createPopover: CreatePopoverState | null = null;
  protected createDrawerOpen = false;
  protected createError = '';
  protected editorMode: 'create' | 'edit' = 'create';
  protected editingEventId: string | null = null;

  protected toastMessage = '';
  protected toastTone: ToastTone = 'info';

  protected draftEventName = '';
  protected draftDescription = '';
  protected draftLocation = '';
  protected draftEventType: WorkspaceCalendarApiEventType = 'TRAINING';
  protected draftDateValue = this.toDateInputValue(new Date());
  protected draftEndDateValue = this.toDateInputValue(new Date());
  protected draftStartTime = '09:00';
  protected draftEndTime = '10:30';
  protected draftTaskTitle = '';
  protected draftTaskDescription = '';
  protected draftTaskAssignedStaffId = '';
  protected taskDraftError = '';
  protected attendeeSearch = '';
  protected attendeeSuggestionsOpen = false;
  protected focusedAttendeeSuggestionIndex = 0;
  protected selectedAttendeeIds: string[] = [CURRENT_USER_ID];
  protected requiredDocumentDrafts: CalendarRequiredDocumentDraft[] = [this.newRequiredDocumentDraft()];
  protected taskDrafts: CalendarTaskDraft[] = [];

  constructor() {
    this.loadEvents();
  }

  @HostListener('document:click', ['$event'])
  protected handleDocumentClick(event: MouseEvent): void {
    const target = event.target;
    if (!(target instanceof Node)) {
      this.closeMonthPicker();
      this.closeViewMenu();
      this.closeMoreOptionsMenu();
      return;
    }

    if (this.monthPickerOpen && !this.monthPicker?.nativeElement.contains(target)) {
      this.closeMonthPicker();
    }

    if (this.viewMenuOpen && !this.viewSelector?.nativeElement.contains(target)) {
      this.closeViewMenu();
    }

    if (this.moreOptionsMenuOpen && !this.moreOptions?.nativeElement.contains(target)) {
      this.closeMoreOptionsMenu();
    }

    if (this.attendeeSuggestionsOpen && !this.attendeePicker?.nativeElement.contains(target)) {
      this.closeAttendeeSuggestions();
    }
  }

  @HostListener('document:keydown', ['$event'])
  protected handleDocumentKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Escape') {
      return;
    }

    if (this.attendeeSuggestionsOpen) {
      event.preventDefault();
      this.closeAttendeeSuggestions();
      return;
    }

    if (this.monthPickerOpen) {
      event.preventDefault();
      this.closeMonthPicker(true);
      return;
    }

    if (this.viewMenuOpen) {
      event.preventDefault();
      this.closeViewMenu(true);
      return;
    }

    if (this.moreOptionsMenuOpen) {
      event.preventDefault();
      this.closeMoreOptionsMenu(true);
    }
  }

  protected get canManageEvents(): boolean {
    return this.currentRole === 'head-coach';
  }

  protected get canModifySelectedEvent(): boolean {
    return this.canModifyEvent(this.selectedEvent);
  }

  protected get isEditMode(): boolean {
    return this.editorMode === 'edit';
  }

  protected get currentRole(): CalendarRole {
    const claims = this.authService?.tokenClaims() ?? null;
    if (!environment.auth.enabled && !claims) {
      return TEMP_DISABLED_AUTH_ROLE;
    }

    return this.hasHeadCoachRole(claims?.roles ?? []) ? 'head-coach' : 'staff';
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

  protected get headerTitle(): string {
    return new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' }).format(this.visibleDate);
  }

  protected get periodUnitLabel(): string {
    if (this.viewMode === 'day') {
      return 'day';
    }

    if (this.viewMode === 'week') {
      return 'week';
    }

    if (this.viewMode === 'year') {
      return 'year';
    }

    return 'month';
  }

  protected get currentViewLabel(): string {
    return this.viewButtonLabel(this.viewMode);
  }

  protected get staffAssigneeOptions(): CalendarPerson[] {
    return this.peopleDirectory.filter((person) => person.group !== 'Player');
  }

  protected get taskDraftStarted(): boolean {
    return Boolean(this.draftTaskTitle.trim() || this.draftTaskDescription.trim() || this.draftTaskAssignedStaffId);
  }

  protected get taskDraftInlineError(): string {
    if (this.taskDraftError) {
      return this.taskDraftError;
    }

    if (!this.taskDraftStarted) {
      return '';
    }

    if (this.draftTaskTitle.trim().length > 80) {
      return 'Task title must be 80 characters or fewer.';
    }

    if (this.draftTaskDescription.trim().length > 300) {
      return 'Task description must be 300 characters or fewer.';
    }

    if (!this.draftTaskTitle.trim()) {
      return 'Task title is required when adding a task.';
    }

    if (!this.draftTaskAssignedStaffId) {
      return 'Assign a staff member for this task.';
    }

    return '';
  }

  protected get filteredAttendeeSuggestions(): CalendarPerson[] {
    const query = this.attendeeSearch.trim().toLowerCase();

    return this.peopleDirectory
      .filter((person) => !this.isAttendeeSelected(person.id))
      .filter((person) => {
        if (!query) {
          return true;
        }

        return person.name.toLowerCase().includes(query) || person.email.toLowerCase().includes(query);
      });
  }

  protected get attendeeSuggestionsAvailable(): boolean {
    return this.peopleDirectory.length > 0;
  }

  protected get attendeeDropdownOpen(): boolean {
    return this.attendeeSuggestionsOpen && this.attendeeSuggestionsAvailable;
  }

  protected get focusedAttendeeSuggestionId(): string | null {
    if (!this.attendeeDropdownOpen || this.focusedAttendeeSuggestionIndex < 0) {
      return null;
    }

    return this.attendeeOptionId(this.focusedAttendeeSuggestionIndex);
  }

  protected get selectedAttendees(): CalendarPerson[] {
    return this.selectedAttendeeIds
      .map((participantId) => this.personById(participantId))
      .filter((participant): participant is CalendarPerson => participant !== null);
  }

  protected get visibleEvents(): CalendarEventView[] {
    return this.events;
  }

  protected get visibleMonthEventCount(): number {
    return this.events.filter(
      (event) => event.start.getFullYear() === this.visibleDate.getFullYear() && event.start.getMonth() === this.visibleDate.getMonth()
    ).length;
  }

  protected get weekDates(): Date[] {
    const start = this.startOfWeek(this.visibleDate);
    return Array.from({ length: 7 }, (_, index) => this.addDays(start, index));
  }

  protected get monthCells(): CalendarDayCell[] {
    return this.buildMonthCells(this.visibleDate);
  }

  protected get miniMonthCells(): CalendarDayCell[] {
    return this.buildMonthCells(this.miniCalendarDate);
  }

  protected get miniMonthTitle(): string {
    return new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' }).format(this.miniCalendarDate);
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

  protected get createDisabled(): boolean {
    return !this.draftEventName.trim() || !this.hasCompleteCreateDateTime;
  }

  protected get hasCompleteCreateDateTime(): boolean {
    return Boolean(this.draftDateValue && this.draftStartTime && this.draftEndDateValue && this.draftEndTime);
  }

  protected get createPreviewDateLabel(): string {
    const start = this.parseDraftDateTime(this.draftDateValue, this.draftStartTime);
    return start ? new Intl.DateTimeFormat('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' }).format(start) : 'Select date and time';
  }

  protected get createPreviewTimeLabel(): string {
    const start = this.parseDraftDateTime(this.draftDateValue, this.draftStartTime);
    const end = this.parseDraftDateTime(this.draftEndDateValue, this.draftEndTime);

    if (!start || !end) {
      return 'Add start and end time';
    }

    return `${this.shortTimeLabel(start)} - ${this.shortTimeLabel(end)}`;
  }

  protected get createPreviewDurationLabel(): string {
    const start = this.parseDraftDateTime(this.draftDateValue, this.draftStartTime);
    const end = this.parseDraftDateTime(this.draftEndDateValue, this.draftEndTime);

    if (!start || !end || end.getTime() <= start.getTime()) {
      return 'Duration appears after start and end are set';
    }

    const durationMinutes = Math.round((end.getTime() - start.getTime()) / 60000);
    const hours = Math.floor(durationMinutes / 60);
    const minutes = durationMinutes % 60;

    if (hours > 0 && minutes > 0) {
      return `${hours}h ${minutes}m`;
    }

    if (hours > 0) {
      return `${hours}h`;
    }

    return `${minutes}m`;
  }

  protected toggleLeftRail(): void {
    this.leftRailOpen = !this.leftRailOpen;
  }

  protected toggleMonthPicker(event: Event): void {
    event.stopPropagation();

    if (this.monthPickerOpen) {
      this.closeMonthPicker();
      return;
    }

    this.openMonthPicker();
  }

  protected handleMonthPickerTriggerKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.openMonthPicker();
    }
  }

  protected showPreviousPickerYear(): void {
    this.monthPickerYear -= 1;
  }

  protected showNextPickerYear(): void {
    this.monthPickerYear += 1;
  }

  protected retryLoad(): void {
    this.loadEvents();
  }

  protected openNewEvent(trigger?: EventTarget | null): void {
    const defaultDate = this.resolveNewEventDate();
    const defaultHour = this.defaultCreateHourForDate(defaultDate);
    this.openCreateDrawer(defaultDate, defaultHour, trigger);
  }

  protected closeLeftRail(): void {
    this.leftRailOpen = false;
  }

  protected selectToolbarMonth(monthIndex: number): void {
    const nextDate = new Date(this.monthPickerYear, monthIndex, 1);
    this.visibleDate = nextDate;
    this.selectedDate = nextDate;
    this.activeDate = nextDate;

    if (this.viewMode === 'year') {
      this.viewMode = 'month';
    }

    this.syncMiniCalendarToDate(nextDate);
    this.closeCreatePopover();
    this.closeMonthPicker(true);
  }

  protected monthPickerButtonLabel(monthIndex: number): string {
    return `${this.monthPickerLabels[monthIndex]} ${this.monthPickerYear}`;
  }

  protected isMonthPickerSelected(monthIndex: number): boolean {
    return this.visibleDate.getFullYear() === this.monthPickerYear && this.visibleDate.getMonth() === monthIndex;
  }

  protected isMonthPickerCurrent(monthIndex: number): boolean {
    const today = new Date();
    return today.getFullYear() === this.monthPickerYear && today.getMonth() === monthIndex;
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
    this.closeCreatePopover();
    this.closeMonthPicker();
  }

  protected toggleViewMenu(event: Event): void {
    event.stopPropagation();

    if (this.viewMenuOpen) {
      this.closeViewMenu();
      return;
    }

    this.openViewMenu();
  }

  protected handleViewTriggerKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.openViewMenu();
    }
  }

  protected selectViewMode(nextView: CalendarViewSelectorMode): void {
    this.setViewMode(nextView);
    this.closeViewMenu(true);
  }

  protected toggleMoreOptionsMenu(event: Event): void {
    event.stopPropagation();

    if (this.moreOptionsMenuOpen) {
      this.closeMoreOptionsMenu();
      return;
    }

    this.openMoreOptionsMenu();
  }

  protected handleMoreOptionsTriggerKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.openMoreOptionsMenu();
    }
  }

  protected refreshCalendar(triggerFocus = true): void {
    this.closeMoreOptionsMenu(triggerFocus);
    this.retryLoad();
  }

  protected handleMoreOptionsMenuKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeMoreOptionsMenu(true);
      return;
    }

    if (event.key === 'ArrowDown' || event.key === 'ArrowUp' || event.key === 'Home' || event.key === 'End') {
      const menu = this.moreOptionsMenu?.nativeElement;
      if (!menu) {
        return;
      }

      event.preventDefault();
      this.focusToolbarMenuItem(menu, event.key);
    }
  }

  protected handleViewMenuItemKeydown(event: KeyboardEvent, view: CalendarViewSelectorMode): void {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.selectViewMode(view);
      return;
    }

    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeViewMenu(true);
      return;
    }

    if (event.key === 'ArrowDown' || event.key === 'ArrowUp' || event.key === 'Home' || event.key === 'End') {
      event.preventDefault();
      this.focusAdjacentViewOption(event.key, view);
    }
  }

  protected goToToday(): void {
    const today = this.startOfDay(new Date());
    this.visibleDate = today;
    this.selectedDate = today;
    this.activeDate = today;
    this.syncMiniCalendarToDate(today);
    this.closeCreatePopover();
    this.closeMonthPicker();
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
    this.syncMiniCalendarToDate(this.visibleDate);
    this.closeCreatePopover();
    this.closeMonthPicker();
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
    this.syncMiniCalendarToDate(this.visibleDate);
    this.closeCreatePopover();
    this.closeMonthPicker();
  }

  protected goPreviousMiniMonth(): void {
    this.miniCalendarDate = new Date(this.miniCalendarDate.getFullYear(), this.miniCalendarDate.getMonth() - 1, 1);
  }

  protected goNextMiniMonth(): void {
    this.miniCalendarDate = new Date(this.miniCalendarDate.getFullYear(), this.miniCalendarDate.getMonth() + 1, 1);
  }

  protected selectMiniMonthDate(date: Date): void {
    this.visibleDate = this.startOfDay(date);
    this.selectedDate = this.startOfDay(date);
    this.activeDate = this.startOfDay(date);
    this.syncMiniCalendarToDate(date);

    if (this.viewMode === 'year') {
      this.viewMode = 'month';
    }

    this.closeCreatePopover();
    this.closeMonthPicker();
  }

  protected selectMonthDate(date: Date): void {
    const nextDate = this.startOfDay(date);
    this.selectedDate = nextDate;
    this.activeDate = nextDate;

    if (nextDate.getMonth() !== this.visibleDate.getMonth() || nextDate.getFullYear() !== this.visibleDate.getFullYear()) {
      this.visibleDate = new Date(nextDate.getFullYear(), nextDate.getMonth(), 1);
    }

    this.syncMiniCalendarToDate(nextDate);
    this.closeCreatePopover();
    this.closeMonthPicker();
  }

  protected chooseMonthCell(cell: CalendarDayCell, event: Event): void {
    if (cell.events.length === 0 && !this.canManageEvents) {
      this.closeCreatePopover();
      return;
    }

    this.selectedDate = this.startOfDay(cell.date);
    this.activeDate = this.startOfDay(cell.date);

    if (cell.events.length === 0 && this.canManageEvents) {
      this.openCreatePopover(cell.date, event.currentTarget);
      return;
    }

    this.closeCreatePopover();
  }

  protected chooseTimeSlot(date: Date, hour: number, event: Event): void {
    if (!this.canManageEvents) {
      this.closeCreatePopover();
      return;
    }

    this.selectedDate = this.startOfDay(date);
    this.activeDate = this.startOfDay(date);
    this.syncMiniCalendarToDate(date);
    this.openCreatePopover(date, event.currentTarget, hour);
  }

  protected closeCreateDrawer(): void {
    const eventIdToReopen = this.isEditMode ? this.editingEventId : null;
    this.createDrawerOpen = false;
    this.createError = '';
    this.editorMode = 'create';
    this.editingEventId = null;

    if (eventIdToReopen) {
      const eventToReopen = this.events.find((event) => event.id === eventIdToReopen) ?? null;
      if (eventToReopen) {
        this.openEvent(eventToReopen);
        return;
      }
    }

    this.restoreFocus();
  }

  protected closeCreatePopover(): void {
    this.createPopover = null;
  }

  protected createFromPopover(trigger?: EventTarget | null): void {
    const date = this.createPopover?.date ?? this.selectedDate;
    const hour = this.createPopover?.hour ?? 9;
    this.closeCreatePopover();
    this.openCreateDrawer(date, hour, trigger);
  }

  protected openAttendeeSuggestions(): void {
    if (!this.attendeeSuggestionsAvailable) {
      return;
    }

    this.attendeeSuggestionsOpen = true;
    this.focusFirstAttendeeSuggestion();
  }

  protected onAttendeeInput(): void {
    this.openAttendeeSuggestions();
  }

  protected handleAttendeeInputKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      if (this.attendeeSuggestionsOpen) {
        event.preventDefault();
        event.stopPropagation();
        this.closeAttendeeSuggestions();
      }
      return;
    }

    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const wasOpen = this.attendeeDropdownOpen;
      this.openAttendeeSuggestions();
      if (wasOpen) {
        this.moveFocusedAttendeeSuggestion(event.key === 'ArrowDown' ? 1 : -1);
      }
      return;
    }

    if (event.key === 'Home' || event.key === 'End') {
      if (!this.attendeeDropdownOpen) {
        return;
      }

      event.preventDefault();
      this.focusedAttendeeSuggestionIndex = event.key === 'Home' ? 0 : this.filteredAttendeeSuggestions.length - 1;
      return;
    }

    if (event.key === 'Enter' && this.attendeeDropdownOpen) {
      const focusedSuggestion = this.filteredAttendeeSuggestions[this.focusedAttendeeSuggestionIndex];
      if (!focusedSuggestion) {
        return;
      }

      event.preventDefault();
      this.addAttendee(focusedSuggestion.id);
    }
  }

  protected closeAttendeeSuggestions(): void {
    this.attendeeSuggestionsOpen = false;
    this.focusedAttendeeSuggestionIndex = 0;
  }

  protected addAttendee(participantId: string): void {
    if (!this.selectedAttendeeIds.includes(participantId)) {
      this.selectedAttendeeIds = [...this.selectedAttendeeIds, participantId];
    }

    this.attendeeSearch = '';
    this.openAttendeeSuggestions();
    window.setTimeout(() => this.attendeeInput?.nativeElement.focus(), 0);
  }

  protected removeAttendee(participantId: string): void {
    this.selectedAttendeeIds = this.selectedAttendeeIds.filter((id) => id !== participantId);
    this.focusFirstAttendeeSuggestion();
  }

  protected isAttendeeSelected(participantId: string): boolean {
    return this.selectedAttendeeIds.includes(participantId);
  }

  protected focusAttendeeSuggestion(index: number): void {
    this.focusedAttendeeSuggestionIndex = index;
  }

  protected attendeeOptionId(index: number): string {
    return `attendee-suggestion-${index}`;
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

  protected addTaskDraft(): void {
    const validationError = this.validateTaskDraftInput();
    this.taskDraftError = validationError ?? '';
    if (validationError) {
      return;
    }

    this.taskDrafts = [
      ...this.taskDrafts,
      {
        id: this.newId('task'),
        title: this.draftTaskTitle.trim(),
        description: this.draftTaskDescription.trim(),
        assignedToActorId: this.draftTaskAssignedStaffId
      }
    ];

    this.clearTaskDraftInput();
  }

  protected removeTaskDraft(taskId: string): void {
    this.taskDrafts = this.taskDrafts.filter((task) => task.id !== taskId);
  }

  protected taskAssignedName(task: CalendarTaskDraft): string {
    return this.personById(task.assignedToActorId)?.name ?? 'Unassigned';
  }

  protected taskDescriptionPreview(task: CalendarTaskDraft): string {
    const description = task.description.trim();
    if (!description) {
      return 'No description';
    }

    return description.length > 90 ? `${description.slice(0, 87)}...` : description;
  }

  protected createEvent(): void {
    if (this.createDisabled) {
      this.createError = 'Title, start, and end are required.';
      return;
    }

    const startAt = this.combineDateAndTime(this.draftDateValue, this.draftStartTime);
    const endAt = this.combineDateAndTime(this.draftEndDateValue, this.draftEndTime);
    this.createError = '';

    if (Date.parse(endAt) <= Date.parse(startAt)) {
      this.createError = 'End date and time must be after start.';
      return;
    }

    if (this.isEditMode) {
      if (!this.editingEventId) {
        this.createError = 'Unable to determine which event to update.';
        return;
      }

      const request: WorkspaceCalendarUpdateEventRequest = {
        title: this.draftEventName.trim(),
        description: this.draftDescription.trim() || null,
        startAt,
        endAt,
        location: this.draftLocation.trim() || null
      };

      const eventId = this.editingEventId;
      this.calendarApi.updateEvent(eventId, request).subscribe({
        next: (eventResponse) => {
          this.createDrawerOpen = false;
          this.createError = '';
          this.editorMode = 'create';
          this.editingEventId = null;
          this.showToast('Event updated', 'success');
          this.loadEvents(eventResponse.eventId);
        },
        error: () => {
          this.createError = 'Unable to update the event.';
        }
      });

      return;
    }

    const taskDraftValidationError = this.validateTaskDraftInput();
    if (taskDraftValidationError) {
      this.taskDraftError = taskDraftValidationError;
      return;
    }

    const tasksPayload = this.taskDrafts.map((task) => ({
      title: task.title,
      description: task.description || '',
      assignedToActorId: task.assignedToActorId || null,
      dueAt: null
    }));

    const request: WorkspaceCalendarCreateEventRequest = {
      title: this.draftEventName.trim(),
      description: this.draftDescription.trim() || null,
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
      tasks: tasksPayload
    };

    this.calendarApi.createEvent(request).subscribe({
      next: (eventResponse) => {
        this.closeCreateDrawer();
        this.showToast(
          tasksPayload.length > 0 ? 'Session created. Assigned staff will be notified.' : 'Event created',
          'success'
        );
        this.loadEvents(eventResponse.eventId);
      },
      error: () => {
        this.createError = 'Unable to create the event.';
      }
    });
  }

  protected openEvent(eventItem: CalendarEventView, trigger?: EventTarget | null): void {
    this.selectedEvent = eventItem;
    this.drawerError = '';
    this.selectedDate = this.startOfDay(eventItem.start);
    this.activeDate = this.startOfDay(eventItem.start);
    this.syncMiniCalendarToDate(eventItem.start);
    this.drawerOpen = true;
    this.closeCreatePopover();

    if (trigger instanceof HTMLElement) {
      this.rememberTrigger(trigger);
    }

    window.setTimeout(() => this.focusOverlay(this.eventDrawer?.nativeElement), 0);
  }

  protected closeDrawer(): void {
    this.drawerOpen = false;
    this.drawerError = '';
    this.selectedEvent = null;
    this.restoreFocus();
  }

  protected editSelectedEvent(trigger?: EventTarget | null): void {
    if (!this.selectedEvent || !this.canModifyEvent(this.selectedEvent)) {
      return;
    }

    this.openEditDrawer(this.selectedEvent, trigger);
  }

  protected deleteSelectedEvent(): void {
    if (!this.selectedEvent || !this.canModifyEvent(this.selectedEvent)) {
      return;
    }

    const confirmed = window.confirm(`Delete "${this.selectedEvent.title}"?`);
    if (!confirmed) {
      return;
    }

    const eventId = this.selectedEvent.id;
    this.drawerError = '';
    this.calendarApi.archiveEvent(eventId).subscribe({
      next: () => {
        this.closeDrawer();
        this.showToast('Event deleted', 'success');
        this.loadEvents();
      },
      error: () => {
        this.drawerError = 'Unable to delete the event.';
      }
    });
  }

  protected closeToast(): void {
    this.toastMessage = '';
  }

  protected viewButtonLabel(viewMode: CalendarViewMode): string {
    if (viewMode === 'month') {
      return 'Month';
    }

    if (viewMode === 'week') {
      return 'Week';
    }

    return viewMode.charAt(0).toUpperCase() + viewMode.slice(1);
  }

  protected eventsForGridDate(date: Date): CalendarEventView[] {
    return this.eventsForDate(date).slice(0, 4);
  }

  protected overflowCount(date: Date): number {
    return Math.max(0, this.eventsForDate(date).length - 4);
  }

  protected popoverDateLabel(date: Date): string {
    return new Intl.DateTimeFormat('en-US', { weekday: 'short', day: 'numeric', month: 'short' }).format(date);
  }

  protected popoverTimeLabel(hour: number): string {
    return this.hourLabel(hour);
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
    this.syncMiniCalendarToDate(nextDate);

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
      if (this.attendeeSuggestionsOpen) {
        event.stopPropagation();
        this.closeAttendeeSuggestions();
        return;
      }

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
    this.syncMiniCalendarToDate(monthDate);
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
        this.loadError = 'Unable to load events.';
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

    const coachName = attendees.find((person) => person.role === 'Head Coach')?.name ?? this.currentUser.name;

    return {
      id: eventResponse.eventId,
      createdByActorId: eventResponse.createdByActorId,
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

  private hasHeadCoachRole(roles: string[]): boolean {
    return roles.some((role) => {
      const normalizedRole = role
        .trim()
        .toLowerCase()
        .replace(/^role[-_:\s]?/, '')
        .replace(/[\s_]+/g, '-');

      return normalizedRole === 'head-coach' || normalizedRole === 'headcoach';
    });
  }

  private eventsForDate(date: Date): CalendarEventView[] {
    return this.visibleEvents.filter((event) => this.sameDay(event.start, date)).sort((left, right) => left.start.getTime() - right.start.getTime());
  }

  private canModifyEvent(eventItem: CalendarEventView | null): boolean {
    if (!eventItem || !this.canManageEvents) {
      return false;
    }

    const creatorId = eventItem.createdByActorId;
    if (!creatorId) {
      return true;
    }

    const currentActorId = this.authService?.currentActorId() ?? null;
    if (!currentActorId) {
      return true;
    }

    return creatorId === currentActorId || creatorId === this.currentUser.id;
  }

  private buildMonthCells(anchorDate: Date): CalendarDayCell[] {
    const monthStart = new Date(anchorDate.getFullYear(), anchorDate.getMonth(), 1);
    const firstDayIndex = (monthStart.getDay() + 6) % 7;
    const gridStart = this.addDays(monthStart, -firstDayIndex);
    const daysInMonth = new Date(monthStart.getFullYear(), monthStart.getMonth() + 1, 0).getDate();
    const cellCount = firstDayIndex + daysInMonth <= 35 ? 35 : 42;

    return Array.from({ length: cellCount }, (_, index) => {
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

    this.closeCreatePopover();
    this.closeMonthPicker();
    this.closeViewMenu();
    this.closeMoreOptionsMenu();
    this.editorMode = 'create';
    this.editingEventId = null;
    this.resetCreateForm(this.startOfDay(date), hour);
    this.selectedEvent = null;
    this.drawerOpen = false;
    this.leftRailOpen = false;
    this.createDrawerOpen = true;

    if (trigger instanceof HTMLElement) {
      this.rememberTrigger(trigger);
    }

    window.setTimeout(() => this.focusOverlay(this.createDrawer?.nativeElement), 0);
  }

  private openEditDrawer(eventItem: CalendarEventView, trigger?: EventTarget | null): void {
    if (!this.canModifyEvent(eventItem)) {
      return;
    }

    this.closeCreatePopover();
    this.closeMonthPicker();
    this.closeViewMenu();
    this.closeMoreOptionsMenu();
    this.prefillFormForEdit(eventItem);
    this.editorMode = 'edit';
    this.editingEventId = eventItem.id;
    this.drawerOpen = false;
    this.leftRailOpen = false;
    this.createDrawerOpen = true;

    if (trigger instanceof HTMLElement) {
      this.rememberTrigger(trigger);
    }

    window.setTimeout(() => this.focusOverlay(this.createDrawer?.nativeElement), 0);
  }

  private openCreatePopover(date: Date, trigger?: EventTarget | null, hour = 9): void {
    if (!this.canManageEvents) {
      return;
    }

    if (trigger instanceof HTMLElement) {
      this.rememberTrigger(trigger);
      const rect = trigger.getBoundingClientRect();
      this.createPopover = {
        date: this.startOfDay(date),
        hour,
        x: Math.max(12, Math.min(rect.left + 12, window.innerWidth - 220)),
        y: Math.max(12, Math.min(rect.top + 42, window.innerHeight - 116))
      };
      return;
    }

    this.createPopover = {
      date: this.startOfDay(date),
      hour,
      x: 16,
      y: 16
    };
  }

  private resetCreateForm(date: Date, hour: number): void {
    const endHour = Math.min(hour + 1, 23);

    this.draftEventName = '';
    this.draftDescription = '';
    this.draftLocation = '';
    this.draftEventType = 'TRAINING';
    this.draftDateValue = this.toDateInputValue(date);
    this.draftEndDateValue = this.toDateInputValue(date);
    this.draftStartTime = `${String(hour).padStart(2, '0')}:00`;
    this.draftEndTime = `${String(endHour).padStart(2, '0')}:30`;
    this.attendeeSearch = '';
    this.closeAttendeeSuggestions();
    this.selectedAttendeeIds = [CURRENT_USER_ID];
    this.requiredDocumentDrafts = [this.newRequiredDocumentDraft()];
    this.clearTaskDraftInput();
    this.taskDrafts = [];
    this.createError = '';
  }

  private prefillFormForEdit(eventItem: CalendarEventView): void {
    this.draftEventName = eventItem.title;
    this.draftDescription = eventItem.description === 'No event description provided.' ? '' : eventItem.description;
    this.draftLocation = eventItem.location === 'No location assigned' ? '' : eventItem.location;
    this.draftEventType = eventItem.type;
    this.draftDateValue = this.toDateInputValue(eventItem.start);
    this.draftEndDateValue = this.toDateInputValue(eventItem.end);
    this.draftStartTime = this.toTimeInputValue(eventItem.start);
    this.draftEndTime = this.toTimeInputValue(eventItem.end);
    this.attendeeSearch = '';
    this.closeAttendeeSuggestions();
    this.selectedAttendeeIds = eventItem.attendees.map((attendee) => attendee.id);
    this.requiredDocumentDrafts =
      eventItem.requiredDocuments.length > 0
        ? eventItem.requiredDocuments.map((document) => ({
            id: this.newId('doc'),
            name: document.name,
            assignedToActorId: document.assignedToActorId ?? ''
          }))
        : [this.newRequiredDocumentDraft()];
    this.clearTaskDraftInput();
    this.taskDrafts = [];
    this.createError = '';
  }

  private validateTaskDraftInput(): string | null {
    if (!this.taskDraftStarted) {
      return null;
    }

    if (this.draftTaskTitle.trim().length > 80) {
      return 'Task title must be 80 characters or fewer.';
    }

    if (this.draftTaskDescription.trim().length > 300) {
      return 'Task description must be 300 characters or fewer.';
    }

    if (!this.draftTaskTitle.trim()) {
      return 'Task title is required when adding a task.';
    }

    if (!this.draftTaskAssignedStaffId) {
      return 'Assign a staff member for this task.';
    }

    return null;
  }

  private clearTaskDraftInput(): void {
    this.draftTaskTitle = '';
    this.draftTaskDescription = '';
    this.draftTaskAssignedStaffId = '';
    this.taskDraftError = '';
  }

  private rememberTrigger(trigger: HTMLElement): void {
    this.lastFocusTrigger = trigger;
  }

  private restoreFocus(): void {
    this.lastFocusTrigger?.focus();
    this.lastFocusTrigger = null;
  }

  private focusOverlay(container: HTMLElement | undefined): void {
    if (!container) {
      return;
    }

    const autofocusTarget = container.querySelector<HTMLElement>('[data-autofocus]');
    const firstFocusable = autofocusTarget ?? this.focusableElements(container)[0] ?? container;
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

  private resolveNewEventDate(): Date {
    if (this.isValidDate(this.selectedDate)) {
      return this.startOfDay(this.selectedDate);
    }

    if (this.isValidDate(this.visibleDate)) {
      return this.startOfDay(this.visibleDate);
    }

    return this.startOfDay(new Date());
  }

  private defaultCreateHourForDate(date: Date): number {
    if (!this.sameDay(date, new Date())) {
      return 9;
    }

    const now = new Date();
    const nextHour = now.getMinutes() > 0 ? now.getHours() + 1 : now.getHours();
    return Math.max(8, Math.min(22, nextHour));
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

  private toTimeInputValue(date: Date): string {
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  }

  private isValidDate(date: Date | null | undefined): date is Date {
    return date instanceof Date && !Number.isNaN(date.getTime());
  }

  private parseDraftDateTime(dateValue: string, timeValue: string): Date | null {
    if (!dateValue || !timeValue) {
      return null;
    }

    const parsed = new Date(`${dateValue}T${timeValue}:00`);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
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

  private syncMiniCalendarToDate(date: Date): void {
    this.miniCalendarDate = new Date(date.getFullYear(), date.getMonth(), 1);
  }

  private openMonthPicker(): void {
    this.closeViewMenu();
    this.closeMoreOptionsMenu();
    this.closeCreatePopover();
    this.monthPickerYear = this.visibleDate.getFullYear();
    this.monthPickerOpen = true;
    window.setTimeout(() => this.focusMonthPickerButton(this.visibleDate.getMonth()), 0);
  }

  private closeMonthPicker(restoreFocus = false): void {
    this.monthPickerOpen = false;

    if (restoreFocus) {
      window.setTimeout(() => this.monthPickerTrigger?.nativeElement.focus(), 0);
    }
  }

  private focusMonthPickerButton(monthIndex: number): void {
    const panel = this.monthPickerPanel?.nativeElement;
    if (!panel) {
      return;
    }

    const selectedButton = panel.querySelector<HTMLElement>(`[data-month-index="${monthIndex}"]`);
    const fallbackButton = panel.querySelector<HTMLElement>('button[data-month-index]');
    (selectedButton ?? fallbackButton)?.focus();
  }

  private openViewMenu(): void {
    this.closeMonthPicker();
    this.closeMoreOptionsMenu();
    this.closeCreatePopover();
    this.viewMenuOpen = true;
    window.setTimeout(() => this.focusViewMenuItem(this.viewMode), 0);
  }

  private closeViewMenu(restoreFocus = false): void {
    this.viewMenuOpen = false;

    if (restoreFocus) {
      window.setTimeout(() => this.viewMenuTrigger?.nativeElement.focus(), 0);
    }
  }

  private focusAdjacentViewOption(key: string, currentView: CalendarViewSelectorMode): void {
    const currentIndex = this.viewOptions.findIndex((option) => option.mode === currentView);
    const fallbackIndex = Math.max(0, currentIndex);
    let nextIndex = fallbackIndex;

    if (key === 'ArrowDown') {
      nextIndex = (fallbackIndex + 1) % this.viewOptions.length;
    } else if (key === 'ArrowUp') {
      nextIndex = (fallbackIndex - 1 + this.viewOptions.length) % this.viewOptions.length;
    } else if (key === 'Home') {
      nextIndex = 0;
    } else if (key === 'End') {
      nextIndex = this.viewOptions.length - 1;
    }

    this.focusViewMenuItem(this.viewOptions[nextIndex].mode);
  }

  private focusViewMenuItem(view: CalendarViewMode): void {
    const menu = this.viewMenu?.nativeElement;
    if (!menu) {
      return;
    }

    const selectedItem = menu.querySelector<HTMLElement>(`[data-view-option="${view}"]`);
    const fallbackItem = menu.querySelector<HTMLElement>('[data-view-option]');
    (selectedItem ?? fallbackItem)?.focus();
  }

  private openMoreOptionsMenu(): void {
    this.closeMonthPicker();
    this.closeViewMenu();
    this.closeCreatePopover();
    this.moreOptionsMenuOpen = true;
    window.setTimeout(() => {
      const menu = this.moreOptionsMenu?.nativeElement;
      if (!menu) {
        return;
      }

      this.focusToolbarMenuItem(menu, 'Home');
    }, 0);
  }

  private closeMoreOptionsMenu(restoreFocus = false): void {
    this.moreOptionsMenuOpen = false;

    if (restoreFocus) {
      window.setTimeout(() => this.moreOptionsTrigger?.nativeElement.focus(), 0);
    }
  }

  private focusToolbarMenuItem(menu: HTMLElement, key: 'ArrowDown' | 'ArrowUp' | 'Home' | 'End'): void {
    const items = Array.from(menu.querySelectorAll<HTMLElement>('button:not([disabled])'));
    if (items.length === 0) {
      return;
    }

    const activeElement = document.activeElement as HTMLElement | null;
    const currentIndex = items.findIndex((item) => item === activeElement);
    let nextIndex = currentIndex >= 0 ? currentIndex : 0;

    if (key === 'ArrowDown') {
      nextIndex = currentIndex >= 0 ? (currentIndex + 1) % items.length : 0;
    } else if (key === 'ArrowUp') {
      nextIndex = currentIndex >= 0 ? (currentIndex - 1 + items.length) % items.length : items.length - 1;
    } else if (key === 'Home') {
      nextIndex = 0;
    } else if (key === 'End') {
      nextIndex = items.length - 1;
    }

    items[nextIndex]?.focus();
  }

  private focusFirstAttendeeSuggestion(): void {
    this.focusedAttendeeSuggestionIndex = this.filteredAttendeeSuggestions.length > 0 ? 0 : -1;
  }

  private moveFocusedAttendeeSuggestion(step: number): void {
    const suggestions = this.filteredAttendeeSuggestions;
    if (suggestions.length === 0) {
      this.focusedAttendeeSuggestionIndex = -1;
      return;
    }

    const currentIndex = this.focusedAttendeeSuggestionIndex >= 0 ? this.focusedAttendeeSuggestionIndex : 0;
    this.focusedAttendeeSuggestionIndex = (currentIndex + step + suggestions.length) % suggestions.length;
  }
}
