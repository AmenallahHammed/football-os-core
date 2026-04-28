import { Injectable, computed, signal } from '@angular/core';
import { FolderNode, WorkspaceDocument } from '../../shared/models/document.model';
import {
  CalendarEvent,
  EventCreateRequest,
  EventParticipant,
  ParticipantGroup,
  RequiredEventDocument
} from '../../shared/models/event.model';
import { WorkspaceNotification, NotificationChannel } from '../../shared/models/notification.model';
import { PlayerProfile } from '../../shared/models/player.model';
import { SearchResult } from '../../shared/models/search.model';

const DEFAULT_PARTICIPANTS: EventParticipant[] = [
  {
    id: 'p-player-1',
    name: 'Leo Carter',
    role: 'Goalkeeper',
    group: 'Player',
    avatarUrl: 'https://images.unsplash.com/photo-1480455624313-e29b44bbfde1?auto=format&fit=crop&w=240&q=80'
  },
  {
    id: 'p-player-2',
    name: 'Marco Silva',
    role: 'Center Back',
    group: 'Player',
    avatarUrl: 'https://images.unsplash.com/photo-1566753323558-f4e0952af115?auto=format&fit=crop&w=240&q=80'
  },
  {
    id: 'p-player-3',
    name: 'Daniel Park',
    role: 'Midfielder',
    group: 'Player',
    avatarUrl: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=240&q=80'
  },
  {
    id: 'p-player-4',
    name: 'Evan Cole',
    role: 'Forward',
    group: 'Player',
    avatarUrl: 'https://images.unsplash.com/photo-1552374196-c4e7ffc6e126?auto=format&fit=crop&w=240&q=80'
  },
  {
    id: 'p-med-1',
    name: 'Dr. Helena Ruiz',
    role: 'Physio Lead',
    group: 'Medical Staff',
    avatarUrl: 'https://images.unsplash.com/photo-1614289371518-722f2615943d?auto=format&fit=crop&w=240&q=80'
  },
  {
    id: 'p-med-2',
    name: 'Aiden Morse',
    role: 'Rehab Specialist',
    group: 'Medical Staff',
    avatarUrl: 'https://images.unsplash.com/photo-1599566150163-29194dcaad36?auto=format&fit=crop&w=240&q=80'
  },
  {
    id: 'p-admin-1',
    name: 'Sara Bennett',
    role: 'Operations Manager',
    group: 'Admin Staff',
    avatarUrl: 'https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=240&q=80'
  },
  {
    id: 'p-admin-2',
    name: 'Nico Alvarez',
    role: 'Match Liaison',
    group: 'Admin Staff',
    avatarUrl: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=240&q=80'
  },
  {
    id: 'p-staff-1',
    name: 'Coach Anton Varga',
    role: 'Head Coach',
    group: 'Staff',
    avatarUrl: 'https://images.unsplash.com/photo-1557862921-37829c790f19?auto=format&fit=crop&w=240&q=80'
  },
  {
    id: 'p-staff-2',
    name: 'Liam Osei',
    role: 'Performance Analyst',
    group: 'Staff',
    avatarUrl: 'https://images.unsplash.com/photo-1595152772835-219674b2a8a6?auto=format&fit=crop&w=240&q=80'
  }
];

@Injectable({
  providedIn: 'root'
})
export class WorkspaceDataService {
  private readonly people = signal<EventParticipant[]>([...DEFAULT_PARTICIPANTS]);

  private readonly events = signal<CalendarEvent[]>(this.createSeedEvents());

  private readonly folders = signal<FolderNode[]>([
    { id: 'folder-contracts', name: 'Contracts', parentId: null },
    { id: 'folder-medical', name: 'Medical', parentId: null },
    { id: 'folder-season-24', name: 'Season 24/25', parentId: 'folder-contracts' },
    { id: 'folder-matchweek-7', name: 'Matchweek 7', parentId: 'folder-season-24' }
  ]);

  private readonly documents = signal<WorkspaceDocument[]>([
    {
      id: 'doc-1',
      name: 'Squad Registration',
      fileType: 'PDF',
      uploadedAt: '2026-04-18',
      status: 'Active',
      folderId: null,
      icon: '[PDF]'
    },
    {
      id: 'doc-2',
      name: 'Transfer Budget Notes',
      fileType: 'DOCX',
      uploadedAt: '2026-04-15',
      status: 'Draft',
      folderId: 'folder-contracts',
      icon: '[DOC]'
    },
    {
      id: 'doc-3',
      name: 'Pre-Season Plan',
      fileType: 'XLSX',
      uploadedAt: '2026-04-10',
      status: 'Active',
      folderId: 'folder-season-24',
      icon: '[XLS]'
    },
    {
      id: 'doc-4',
      name: 'Player Recovery Protocol',
      fileType: 'DOCX',
      uploadedAt: '2026-04-08',
      status: 'Archived',
      folderId: 'folder-medical',
      icon: '[FILE]'
    },
    {
      id: 'doc-5',
      name: 'Matchweek 7 Checklist',
      fileType: 'PDF',
      uploadedAt: '2026-04-21',
      status: 'Active',
      folderId: 'folder-matchweek-7',
      icon: '[PDF]'
    }
  ]);

  private readonly players = signal<PlayerProfile[]>([
    {
      id: 'player-1',
      name: 'Leo Carter',
      position: 'GK',
      shirtNumber: 1,
      team: 'First Team',
      photoUrl: 'https://images.unsplash.com/photo-1480455624313-e29b44bbfde1?auto=format&fit=crop&w=900&q=80',
      documents: ['Contract Renewal.pdf', 'Medical Clearance.docx'],
      events: ['Goalkeeper Session - Tue 09:00', 'Media Day - Fri 14:00']
    },
    {
      id: 'player-2',
      name: 'Marco Silva',
      position: 'DEF',
      shirtNumber: 4,
      team: 'First Team',
      photoUrl: 'https://images.unsplash.com/photo-1566753323558-f4e0952af115?auto=format&fit=crop&w=900&q=80',
      documents: ['Performance Review.pdf', 'Defensive Metrics.xlsx'],
      events: ['Tactical Drill - Wed 11:00', 'Recovery Session - Thu 10:00']
    },
    {
      id: 'player-3',
      name: 'Daniel Park',
      position: 'MID',
      shirtNumber: 8,
      team: 'First Team',
      photoUrl: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=900&q=80',
      documents: ['Scouting Snapshot.pdf', 'Contract Clause Addendum.docx'],
      events: ['Midfield Unit Session - Mon 16:00', 'Cup Match Briefing - Fri 18:00']
    },
    {
      id: 'player-4',
      name: 'Evan Cole',
      position: 'FWD',
      shirtNumber: 9,
      team: 'First Team',
      photoUrl: 'https://images.unsplash.com/photo-1552374196-c4e7ffc6e126?auto=format&fit=crop&w=900&q=80',
      documents: ['Shooting Analysis.pdf', 'Bonus Structure.xlsx'],
      events: ['Finishing Drill - Tue 14:00', 'Sponsor Event - Thu 17:30']
    }
  ]);

  private readonly notifications = signal<WorkspaceNotification[]>([
    {
      id: 'notif-1',
      action: 'Document approved',
      resource: 'Transfer Budget Notes',
      timestamp: '2m ago',
      unread: true,
      channel: 'notifications'
    },
    {
      id: 'notif-2',
      action: 'Event updated',
      resource: 'League Match vs Lyon',
      timestamp: '14m ago',
      unread: true,
      channel: 'notifications'
    },
    {
      id: 'notif-3',
      action: 'Inbox message',
      resource: 'Board: Quarterly Targets',
      timestamp: '37m ago',
      unread: true,
      channel: 'inbox'
    },
    {
      id: 'notif-4',
      action: 'Scouting upload',
      resource: 'South America Report',
      timestamp: '1h ago',
      unread: false,
      channel: 'inbox'
    }
  ]);

  readonly unreadNotificationCount = computed(
    () => this.notifications().filter((item) => item.channel === 'notifications' && item.unread).length
  );

  readonly unreadInboxCount = computed(
    () => this.notifications().filter((item) => item.channel === 'inbox' && item.unread).length
  );

  getCalendarEvents(): CalendarEvent[] {
    return [...this.events()].sort((a, b) => {
      const byDate = a.date.localeCompare(b.date);
      if (byDate !== 0) {
        return byDate;
      }
      return a.time.localeCompare(b.time);
    });
  }

  getEventsForDate(date: Date): CalendarEvent[] {
    const isoDate = this.toIsoDate(date);
    return this.getCalendarEvents().filter((event) => event.date === isoDate);
  }

  getUpcomingEvents(limit = 6): CalendarEvent[] {
    const today = this.toIsoDate(new Date());
    return this.getCalendarEvents().filter((event) => event.date >= today).slice(0, limit);
  }

  getParticipants(): EventParticipant[] {
    return [...this.people()];
  }

  getParticipantsByGroup(group: ParticipantGroup | 'All'): EventParticipant[] {
    if (group === 'All') {
      return this.getParticipants();
    }
    return this.people().filter((participant) => participant.group === group);
  }

  addCalendarEvent(request: EventCreateRequest): void {
    const normalizedDocuments = request.requiredDocuments
      .filter((document) => document.name.trim())
      .map((document) => ({
        ...document,
        id: document.id || this.newId('req')
      }));

    const event: CalendarEvent = {
      id: this.newId('ev'),
      title: request.title.trim() || 'Untitled Event',
      date: request.date,
      time: '09:00 AM',
      type: 'Training',
      location: request.usage.trim() || 'Main Pitch',
      coachName: this.primaryCoachName(request.attendees),
      notes: request.tasks[0]?.description ? `"${request.tasks[0].description}"` : '"No notes submitted."',
      attendees: request.attendees,
      requiredDocuments: normalizedDocuments,
      opponent: undefined
    };

    this.events.update((current) => [...current, event]);
  }

  markRequiredDocumentUploaded(eventId: string, documentId: string): void {
    this.events.update((current) =>
      current.map((event) =>
        event.id === eventId
          ? {
              ...event,
              requiredDocuments: event.requiredDocuments.map((document) =>
                document.id === documentId
                  ? {
                      ...document,
                      uploaded: true
                    }
                  : document
              )
            }
          : event
      )
    );
  }

  renameRequiredDocument(eventId: string, documentId: string, nextName: string): void {
    const trimmed = nextName.trim();
    if (!trimmed) {
      return;
    }

    this.events.update((current) =>
      current.map((event) =>
        event.id === eventId
          ? {
              ...event,
              requiredDocuments: event.requiredDocuments.map((document) =>
                document.id === documentId
                  ? {
                      ...document,
                      name: trimmed
                    }
                  : document
              )
            }
          : event
      )
    );
  }

  deleteRequiredDocument(eventId: string, documentId: string): void {
    this.events.update((current) =>
      current.map((event) =>
        event.id === eventId
          ? {
              ...event,
              requiredDocuments: event.requiredDocuments.filter((document) => document.id !== documentId)
            }
          : event
      )
    );
  }

  getFolders(): FolderNode[] {
    return [...this.folders()];
  }

  getDocuments(): WorkspaceDocument[] {
    return [...this.documents()];
  }

  createFolder(name: string, parentId: string | null): void {
    if (!name.trim()) {
      return;
    }

    this.folders.update((current) => [
      ...current,
      {
        id: this.newId('folder'),
        name: name.trim(),
        parentId
      }
    ]);
  }

  uploadDocuments(files: File[], folderId: string | null): void {
    if (!files.length) {
      return;
    }

    const uploadedAt = this.toIsoDate(new Date());
    const nextDocs = files.map((file) => ({
      id: this.newId('doc'),
      name: file.name,
      fileType: this.resolveFileType(file.name),
      uploadedAt,
      status: 'Draft' as const,
      folderId,
      icon: this.resolveFileIcon(file.name)
    }));

    this.documents.update((current) => [...nextDocs, ...current]);
  }

  renameDocument(documentId: string, newName: string): void {
    const trimmed = newName.trim();
    if (!trimmed) {
      return;
    }

    this.documents.update((current) =>
      current.map((document) =>
        document.id === documentId
          ? {
              ...document,
              name: trimmed
            }
          : document
      )
    );
  }

  moveDocument(documentId: string, folderId: string | null): void {
    this.documents.update((current) =>
      current.map((document) =>
        document.id === documentId
          ? {
              ...document,
              folderId
            }
          : document
      )
    );
  }

  archiveDocument(documentId: string): void {
    this.documents.update((current) =>
      current.map((document) =>
        document.id === documentId
          ? {
              ...document,
              status: 'Archived'
            }
          : document
      )
    );
  }

  deleteDocument(documentId: string): void {
    this.documents.update((current) => current.filter((document) => document.id !== documentId));
  }

  getPlayers(): PlayerProfile[] {
    return [...this.players()];
  }

  getPlayerById(playerId: string): PlayerProfile | null {
    return this.players().find((player) => player.id === playerId) ?? null;
  }

  getChannelNotifications(channel: NotificationChannel): WorkspaceNotification[] {
    return this.notifications().filter((item) => item.channel === channel);
  }

  markNotificationRead(notificationId: string): void {
    this.notifications.update((current) =>
      current.map((item) =>
        item.id === notificationId
          ? {
              ...item,
              unread: false
            }
          : item
      )
    );
  }

  markAllAsRead(channel: NotificationChannel): void {
    this.notifications.update((current) =>
      current.map((item) =>
        item.channel === channel
          ? {
              ...item,
              unread: false
            }
          : item
      )
    );
  }

  search(query: string): SearchResult[] {
    const normalized = query.trim().toLowerCase();
    if (!normalized) {
      return [];
    }

    const documentResults = this.documents()
      .filter((document) => document.name.toLowerCase().includes(normalized))
      .map<SearchResult>((document) => ({
        id: document.id,
        type: 'Document',
        title: document.name,
        context: `${document.fileType} - ${document.status}`
      }));

    const eventResults = this.events()
      .filter((event) => event.title.toLowerCase().includes(normalized))
      .map<SearchResult>((event) => ({
        id: event.id,
        type: 'Event',
        title: event.title,
        context: `${event.date} - ${event.location}`
      }));

    return [...documentResults, ...eventResults];
  }

  private createSeedEvents(): CalendarEvent[] {
    return [
      {
        id: 'ev-1',
        title: 'Explosive Lower Body Block',
        date: this.referenceDate(3),
        time: '08:30 AM',
        type: 'Training',
        location: 'Performance Dome',
        coachName: 'Coach Anton Varga',
        notes: '"Acceleration starts, resisted sprints, and contact prep for the first team."',
        attendees: this.seedAttendees(['p-staff-1', 'p-player-2', 'p-player-3', 'p-med-1', 'p-admin-1']),
        requiredDocuments: [
          this.seedDocument('req-1', 'Session Plan v3.pdf', 'p-staff-2', false),
          this.seedDocument('req-2', 'Injury Clearance - Silva.docx', 'p-med-1', true)
        ]
      },
      {
        id: 'ev-2',
        title: 'Finishing Wave Review',
        date: this.referenceDate(3),
        time: '02:00 PM',
        type: 'Drills',
        location: 'Analysis Bay',
        coachName: 'Coach Anton Varga',
        notes: '"Clip review focused on box entries, second-ball habits, and final-shot timing."',
        attendees: this.seedAttendees(['p-staff-1', 'p-staff-2', 'p-player-1', 'p-player-4']),
        requiredDocuments: [this.seedDocument('req-3', 'Video Tag Report.xlsx', 'p-staff-2', false)]
      },
      {
        id: 'ev-3',
        title: 'League Match vs Marseille',
        date: this.referenceDate(8),
        time: '07:45 PM',
        type: 'Match',
        location: 'North Stand Arena',
        coachName: 'Coach Anton Varga',
        notes: '"High press in the first 20 minutes and hold compactness after transition."',
        opponent: 'Marseille',
        attendees: this.seedAttendees(['p-staff-1', 'p-admin-2', 'p-player-1', 'p-player-2', 'p-player-3', 'p-player-4']),
        requiredDocuments: [
          this.seedDocument('req-4', 'Matchday Logistics.pdf', 'p-admin-2', true),
          this.seedDocument('req-5', 'Set Piece Deck.pptx', 'p-staff-2', true)
        ]
      },
      {
        id: 'ev-4',
        title: 'Recovery Pool Reset',
        date: this.referenceDate(10),
        time: '10:00 AM',
        type: 'Recovery',
        location: 'Hydro Lab',
        coachName: 'Dr. Helena Ruiz',
        notes: '"Contrast circuit, hydration markers, and low-load mobility for match starters."',
        attendees: this.seedAttendees(['p-med-1', 'p-med-2', 'p-player-2', 'p-player-3']),
        requiredDocuments: [this.seedDocument('req-6', 'Recovery Checklist.pdf', 'p-med-2', false)]
      },
      {
        id: 'ev-5',
        title: 'Academy Sprint Lab',
        date: this.referenceDate(12),
        time: '11:30 AM',
        type: 'Academy',
        location: 'Youth Pitch',
        coachName: 'Liam Osei',
        notes: '"U21 repeat sprint mechanics and transition shape under fatigue."',
        attendees: this.seedAttendees(['p-staff-2', 'p-admin-1', 'p-player-2', 'p-player-4']),
        requiredDocuments: [
          this.seedDocument('req-7', 'Academy Load Sheet.pdf', 'p-admin-1', true),
          this.seedDocument('req-8', 'Sprint Split Tracker.xlsx', 'p-staff-2', false)
        ]
      },
      {
        id: 'ev-6',
        title: 'Pressing Waves',
        date: this.referenceDate(12),
        time: '04:00 PM',
        type: 'Training',
        location: 'Pitch One',
        coachName: 'Coach Anton Varga',
        notes: '"Trigger recognition across the front three with compact rest defense."',
        attendees: this.seedAttendees(['p-staff-1', 'p-player-1', 'p-player-2', 'p-player-3', 'p-player-4']),
        requiredDocuments: [this.seedDocument('req-9', 'Pressing Script.pdf', 'p-staff-2', true)]
      },
      {
        id: 'ev-7',
        title: 'Cup Match vs Lille',
        date: this.referenceDate(15),
        time: '08:00 PM',
        type: 'Match',
        location: 'Grand Stade',
        coachName: 'Coach Anton Varga',
        notes: '"Fast ball circulation and aggressive box occupation on the weak side."',
        opponent: 'Lille',
        attendees: this.seedAttendees(['p-staff-1', 'p-admin-2', 'p-player-1', 'p-player-2', 'p-player-3', 'p-player-4']),
        requiredDocuments: [
          this.seedDocument('req-10', 'Travel Run Sheet.pdf', 'p-admin-2', true),
          this.seedDocument('req-11', 'Opponent Corners Deck.pptx', 'p-staff-2', false)
        ]
      },
      {
        id: 'ev-8',
        title: 'Physio Recovery Checks',
        date: this.referenceDate(18),
        time: '09:15 AM',
        type: 'Recovery',
        location: 'Medical Wing',
        coachName: 'Dr. Helena Ruiz',
        notes: '"Soft tissue screening and force plate review before the midweek reload."',
        attendees: this.seedAttendees(['p-med-1', 'p-med-2', 'p-player-1', 'p-player-3']),
        requiredDocuments: [this.seedDocument('req-12', 'Force Plate Export.csv', 'p-med-1', false)]
      },
      {
        id: 'ev-9',
        title: 'Final Third Circuit',
        date: this.referenceDate(20),
        time: '03:30 PM',
        type: 'Drills',
        location: 'Finishing Grid',
        coachName: 'Coach Anton Varga',
        notes: '"Three-lane finishing pattern with rapid resets and rebound reactions."',
        attendees: this.seedAttendees(['p-staff-1', 'p-player-2', 'p-player-3', 'p-player-4']),
        requiredDocuments: [this.seedDocument('req-13', 'Chance Creation Script.pdf', 'p-staff-2', true)]
      },
      {
        id: 'ev-10',
        title: 'Academy Tactical Lab',
        date: this.referenceDate(22),
        time: '01:00 PM',
        type: 'Academy',
        location: 'Tactical Hall',
        coachName: 'Liam Osei',
        notes: '"Line breaking patterns and possession exits for the academy block."',
        attendees: this.seedAttendees(['p-staff-2', 'p-admin-1', 'p-player-2', 'p-player-3']),
        requiredDocuments: [this.seedDocument('req-14', 'Academy Clips Deck.pdf', 'p-staff-2', false)]
      },
      {
        id: 'ev-11',
        title: 'Travel Reset',
        date: this.referenceDate(24),
        time: '11:00 AM',
        type: 'Recovery',
        location: 'Recovery Suite',
        coachName: 'Aiden Morse',
        notes: '"Low-load flush session, sleep check, and arrival readiness after travel."',
        attendees: this.seedAttendees(['p-med-2', 'p-player-1', 'p-player-4']),
        requiredDocuments: [this.seedDocument('req-15', 'Travel Wellness Form.pdf', 'p-med-2', true)]
      },
      {
        id: 'ev-12',
        title: 'Set Piece Rehearsal',
        date: this.referenceDate(27),
        time: '05:15 PM',
        type: 'Training',
        location: 'Pitch A',
        coachName: 'Coach Anton Varga',
        notes: '"Rehearse corner routines, second-phase coverage, and back-post timing."',
        attendees: this.seedAttendees(['p-staff-1', 'p-player-1', 'p-player-2', 'p-player-3', 'p-player-4']),
        requiredDocuments: [this.seedDocument('req-16', 'Set Piece Call Sheet.pdf', 'p-staff-2', true)]
      },
      {
        id: 'ev-13',
        title: 'League Match vs Monaco',
        date: this.referenceDate(29),
        time: '06:30 PM',
        type: 'Match',
        location: 'North Stand Arena',
        coachName: 'Coach Anton Varga',
        notes: '"Protect the middle lane and attack early switches in possession."',
        opponent: 'Monaco',
        attendees: this.seedAttendees(['p-staff-1', 'p-admin-2', 'p-player-1', 'p-player-2', 'p-player-3', 'p-player-4']),
        requiredDocuments: [
          this.seedDocument('req-17', 'Matchday Ops Sheet.pdf', 'p-admin-2', true),
          this.seedDocument('req-18', 'Monaco Press Notes.docx', 'p-staff-2', true)
        ]
      }
    ];
  }

  private seedAttendees(ids: string[]): EventParticipant[] {
    return ids
      .map((id) => DEFAULT_PARTICIPANTS.find((participant) => participant.id === id))
      .filter((participant): participant is EventParticipant => !!participant);
  }

  private seedDocument(
    id: string,
    name: string,
    responsibleStaffId: string,
    uploaded: boolean
  ): RequiredEventDocument {
    const owner = DEFAULT_PARTICIPANTS.find((participant) => participant.id === responsibleStaffId);
    return {
      id,
      name,
      responsibleStaffId,
      responsibleStaffName: owner?.name ?? 'Unknown Staff',
      uploaded
    };
  }

  private primaryCoachName(attendees: EventParticipant[]): string {
    const coach = attendees.find((participant) => participant.group === 'Staff');
    return coach?.name ?? 'Staff Assignment Pending';
  }

  private referenceDate(day: number): string {
    return `2026-08-${String(day).padStart(2, '0')}`;
  }

  private toIsoDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  private newId(prefix: string): string {
    return `${prefix}-${Math.random().toString(36).slice(2, 9)}`;
  }

  private resolveFileType(fileName: string): string {
    const extension = fileName.split('.').pop()?.toUpperCase();
    return extension ?? 'FILE';
  }

  private resolveFileIcon(fileName: string): string {
    const extension = fileName.split('.').pop()?.toLowerCase();
    if (extension === 'pdf') {
      return '[PDF]';
    }
    if (extension === 'docx' || extension === 'doc') {
      return '[DOC]';
    }
    if (extension === 'xlsx' || extension === 'csv') {
      return '[XLS]';
    }
    return '[FILE]';
  }
}
