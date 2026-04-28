import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';

export type WorkspaceCalendarApiEventType = 'TRAINING' | 'MATCH' | 'MEETING' | 'MEDICAL_CHECK' | 'ADMINISTRATIVE' | 'OTHER';
export type WorkspaceCalendarApiCanonicalType = 'PLAYER' | 'TEAM' | 'MATCH' | 'TRAINING_SESSION' | 'CLUB';

export interface WorkspaceCalendarApiAttendee {
  canonicalRef: {
    type: WorkspaceCalendarApiCanonicalType;
    id: string;
  };
  mandatory: boolean;
  confirmed: boolean;
}

export interface WorkspaceCalendarApiRequiredDocument {
  requirementId: string;
  description: string;
  documentCategory: string;
  assignedToActorId: string | null;
  submittedDocumentId: string | null;
  submitted: boolean;
}

export interface WorkspaceCalendarApiTask {
  taskId: string;
  title: string;
  description: string;
  assignedToActorId: string | null;
  dueAt: string | null;
  completed: boolean;
  completedAt: string | null;
}

export interface WorkspaceCalendarApiEvent {
  eventId: string;
  title: string;
  description: string | null;
  type: WorkspaceCalendarApiEventType;
  startAt: string;
  endAt: string;
  location: string | null;
  createdByActorId: string | null;
  teamRefId: string | null;
  state: string;
  attendees: WorkspaceCalendarApiAttendee[];
  requiredDocuments: WorkspaceCalendarApiRequiredDocument[];
  tasks: WorkspaceCalendarApiTask[];
  reminderSent: boolean;
  createdAt: string;
}

export interface WorkspaceCalendarCreateEventRequest {
  title: string;
  description: string | null;
  type: WorkspaceCalendarApiEventType;
  startAt: string;
  endAt: string;
  location: string | null;
  attendees: Array<{
    actorId: string;
    mandatory: boolean;
    canonicalType: 'PLAYER' | 'CLUB';
  }>;
  requiredDocuments: Array<{
    description: string;
    documentCategory: string;
    assignedToActorId: string | null;
  }>;
  tasks: Array<{
    title: string;
    description: string;
    assignedToActorId: string | null;
    dueAt: string | null;
  }>;
}

interface WorkspaceCalendarPageResponse<T> {
  content: T[];
}

@Injectable({
  providedIn: 'root'
})
export class WorkspaceCalendarApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080';
  private readonly teamRefId = '00000000-0000-0000-0000-000000000001';

  listEvents(): Observable<WorkspaceCalendarApiEvent[]> {
    const params = new HttpParams().set('teamRefId', this.teamRefId).set('size', '200');

    return this.http
      .get<WorkspaceCalendarPageResponse<WorkspaceCalendarApiEvent>>(`${this.baseUrl}/api/v1/events`, { params })
      .pipe(map((response) => response.content ?? []));
  }

  createEvent(request: WorkspaceCalendarCreateEventRequest): Observable<WorkspaceCalendarApiEvent> {
    return this.http.post<WorkspaceCalendarApiEvent>(`${this.baseUrl}/api/v1/events`, {
      ...request,
      teamRefId: this.teamRefId
    });
  }

  deleteEvent(eventId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/v1/events/${eventId}`);
  }
}
