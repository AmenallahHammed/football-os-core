export type EventType = 'Match' | 'Training' | 'Meeting' | 'Medical' | 'Recovery' | 'Academy' | 'Drills';
export type ParticipantGroup = 'Player' | 'Medical Staff' | 'Admin Staff' | 'Staff';

export interface EventParticipant {
  id: string;
  name: string;
  role: string;
  group: ParticipantGroup;
  avatarUrl: string;
}

export interface RequiredEventDocument {
  id: string;
  name: string;
  responsibleStaffId: string;
  responsibleStaffName: string;
  uploaded: boolean;
}

export interface EventTask {
  id: string;
  description: string;
  assigneeIds: string[];
}

export interface CalendarEvent {
  id: string;
  title: string;
  date: string;
  time: string;
  type: EventType;
  location: string;
  coachName: string;
  notes: string;
  opponent?: string;
  attendees: EventParticipant[];
  requiredDocuments: RequiredEventDocument[];
}

export interface EventCreateRequest {
  date: string;
  title: string;
  usage: string;
  attendees: EventParticipant[];
  requiredDocuments: RequiredEventDocument[];
  tasks: EventTask[];
}
