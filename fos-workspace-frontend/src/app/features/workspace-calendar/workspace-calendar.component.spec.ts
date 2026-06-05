import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import {
  WorkspaceCalendarApiEvent,
  WorkspaceCalendarApiService
} from './workspace-calendar-api.service';
import { WorkspaceCalendarComponent } from './workspace-calendar.component';

describe('WorkspaceCalendarComponent event drawer actions', () => {
  let fixture: ComponentFixture<WorkspaceCalendarComponent>;
  let component: WorkspaceCalendarComponent;
  let calendarApi: jasmine.SpyObj<WorkspaceCalendarApiService>;
  let authService: jasmine.SpyObj<Pick<AuthService, 'isHeadCoach' | 'currentActorId'>>;

  beforeEach(async () => {
    calendarApi = jasmine.createSpyObj('WorkspaceCalendarApiService', ['listEvents', 'createEvent', 'updateEvent', 'archiveEvent']);
    authService = jasmine.createSpyObj('AuthService', ['isHeadCoach', 'currentActorId']);
    authService.currentActorId.and.returnValue('11111111-1111-1111-1111-111111111101');

    await TestBed.configureTestingModule({
      imports: [WorkspaceCalendarComponent],
      providers: [
        { provide: WorkspaceCalendarApiService, useValue: calendarApi },
        { provide: AuthService, useValue: authService }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('shows edit and delete buttons for a head coach', fakeAsync(() => {
    setupComponent(true, [buildEventResponse()]);
    openSelectedEvent();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[aria-label="Edit event"]')).not.toBeNull();
    expect(root.querySelector('[aria-label="Delete event"]')).not.toBeNull();
  }));

  it('hides edit and delete buttons for a non-head-coach user', fakeAsync(() => {
    setupComponent(false, [buildEventResponse()]);
    openSelectedEvent();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[aria-label="Edit event"]')).toBeNull();
    expect(root.querySelector('[aria-label="Delete event"]')).toBeNull();
  }));

  it('opens the edit form with the selected event values', fakeAsync(() => {
    setupComponent(true, [buildEventResponse()]);
    openSelectedEvent();

    clickSelector('[aria-label="Edit event"]');
    tick();
    fixture.detectChanges();

    expect(component['isEditMode']).toBeTrue();
    expect(component['draftEventName']).toBe('Morning Training');
    expect(component['draftLocation']).toBe('Main Pitch');
    expect(component['selectedAttendeeIds']).toEqual([
      '11111111-1111-1111-1111-111111111102',
      '44444444-4444-4444-4444-444444444401'
    ]);
  }));

  it('updates the selected event and refreshes the drawer details', fakeAsync(() => {
    const initial = buildEventResponse();
    const updated = buildEventResponse({
      title: 'Updated Training',
      startAt: '2026-06-05T11:00:00Z',
      endAt: '2026-06-05T12:30:00Z'
    });

    setupComponent(true, [initial], [updated]);
    calendarApi.updateEvent.and.returnValue(of(updated));
    openSelectedEvent();

    clickSelector('[aria-label="Edit event"]');
    tick();
    fixture.detectChanges();

    const titleInput = fixture.nativeElement.querySelector('input[placeholder="Add a title"]') as HTMLInputElement;
    titleInput.value = 'Updated Training';
    titleInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    clickButton('Save changes');
    tick();
    fixture.detectChanges();

    expect(calendarApi.updateEvent).toHaveBeenCalledWith(
      initial.eventId,
      jasmine.objectContaining({
        title: 'Updated Training',
        location: 'Main Pitch'
      })
    );
    expect(component['selectedEvent']?.title).toBe('Updated Training');
    expect(component['drawerOpen']).toBeTrue();
    flush();
  }));

  it('opens a confirmation dialog before deleting', fakeAsync(() => {
    setupComponent(true, [buildEventResponse()]);
    openSelectedEvent();

    clickSelector('[aria-label="Delete event"]');
    tick();
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[role="alertdialog"]')).not.toBeNull();
    expect(root.textContent).toContain('Are you sure you want to delete this event? This action cannot be undone.');
  }));

  it('deletes the event, closes the drawer, and removes it from the calendar state', fakeAsync(() => {
    const eventResponse = buildEventResponse();
    setupComponent(true, [eventResponse], []);
    calendarApi.archiveEvent.and.returnValue(of(void 0));
    openSelectedEvent();

    clickSelector('[aria-label="Delete event"]');
    tick();
    fixture.detectChanges();

    clickButton('Delete event');
    tick();
    fixture.detectChanges();

    expect(calendarApi.archiveEvent).toHaveBeenCalledWith(eventResponse.eventId);
    expect(component['drawerOpen']).toBeFalse();
    expect(component['events'].length).toBe(0);
    flush();
  }));

  function setupComponent(initialHeadCoach: boolean, firstLoad: WorkspaceCalendarApiEvent[], secondLoad: WorkspaceCalendarApiEvent[] = firstLoad): void {
    authService.isHeadCoach.and.returnValue(initialHeadCoach);
    calendarApi.listEvents.and.returnValues(of(firstLoad), of(secondLoad));
    calendarApi.createEvent.and.returnValue(of(buildEventResponse()));
    calendarApi.updateEvent.and.returnValue(of(buildEventResponse()));
    calendarApi.archiveEvent.and.returnValue(of(void 0));

    fixture = TestBed.createComponent(WorkspaceCalendarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
  }

  function openSelectedEvent(): void {
    component['openEvent'](component['events'][0]);
    tick();
    fixture.detectChanges();
  }

  function clickSelector(selector: string): void {
    const button = fixture.nativeElement.querySelector(selector) as HTMLButtonElement | null;
    expect(button).withContext(`Expected selector ${selector} to resolve to a button`).not.toBeNull();
    button?.click();
    fixture.detectChanges();
  }

  function clickButton(label: string): void {
    const button = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(
      (candidate) => candidate.textContent?.trim() === label
    );

    expect(button).withContext(`Expected a button with text "${label}"`).toBeDefined();
    button?.click();
    fixture.detectChanges();
  }

  function buildEventResponse(overrides: Partial<WorkspaceCalendarApiEvent> = {}): WorkspaceCalendarApiEvent {
    return {
      eventId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      title: 'Morning Training',
      description: 'Pitch and gym rotation',
      type: 'TRAINING',
      startAt: '2026-06-05T09:00:00Z',
      endAt: '2026-06-05T10:30:00Z',
      location: 'Main Pitch',
      createdByActorId: '00000000-0000-0000-0000-000000000001',
      teamRefId: '00000000-0000-0000-0000-000000000001',
      state: 'ACTIVE',
      attendees: [
        {
          canonicalRef: { type: 'CLUB', id: '11111111-1111-1111-1111-111111111102' },
          mandatory: true,
          confirmed: true
        },
        {
          canonicalRef: { type: 'PLAYER', id: '44444444-4444-4444-4444-444444444401' },
          mandatory: true,
          confirmed: true
        }
      ],
      requiredDocuments: [
        {
          requirementId: 'doc-1',
          description: 'Medical clearance',
          documentCategory: 'GENERAL',
          assignedToActorId: '11111111-1111-1111-1111-111111111102',
          submittedDocumentId: null,
          submitted: false
        }
      ],
      tasks: [],
      reminderSent: false,
      createdAt: '2026-06-04T08:00:00Z',
      ...overrides
    };
  }
});
