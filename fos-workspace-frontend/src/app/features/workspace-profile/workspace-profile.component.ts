import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { WorkspaceDataService } from '../../core/data/workspace-data.service';
import { EventParticipant } from '../../shared/models/event.model';
import { WorkspaceNotification } from '../../shared/models/notification.model';
import { WorkspaceCalendarApiEvent, WorkspaceCalendarApiService, WorkspaceCalendarApiTask } from '../workspace-calendar/workspace-calendar-api.service';

interface ProfileInfoField {
  label: string;
  value: string;
  provided: boolean;
}

type TaskStatus = 'Pending' | 'Done' | 'Overdue';

interface ProfileTaskItem {
  id: string;
  title: string;
  description: string;
  relatedSession: string | null;
  dueAt: string | null;
  status: TaskStatus;
  assignedBy: string | null;
}

interface ProfileMissingDocumentItem {
  id: string;
  name: string;
  relatedSession: string | null;
  dueAt: string | null;
  assignedBy: string | null;
  status: 'Missing';
}

@Component({
  selector: 'app-workspace-profile',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './workspace-profile.component.html',
  styleUrl: './workspace-profile.component.scss'
})
export class WorkspaceProfileComponent implements OnInit, OnDestroy {
  // TODO(profile-api): Replace fallback profile fields and local document mapping when a dedicated
  // workspace profile endpoint exposes phone, job title, joined date, and actor-scoped assignments.
  private static readonly FALLBACK_ACTOR_ID = '11111111-1111-1111-1111-111111111101';
  private static readonly DEFAULT_AVATAR_URL =
    'https://images.unsplash.com/photo-1557862921-37829c790f19?auto=format&fit=crop&w=300&q=80';

  private readonly authService = inject(AuthService);
  private readonly workspaceData = inject(WorkspaceDataService);
  private readonly calendarApi = inject(WorkspaceCalendarApiService);
  private readonly subscriptions = new Subscription();

  protected isLoadingAssignments = true;
  protected assignmentLoadError = '';
  protected assignedTasks: ProfileTaskItem[] = [];
  protected missingDocuments: ProfileMissingDocumentItem[] = [];
  protected recentActivity: WorkspaceNotification[] = [];

  ngOnInit(): void {
    this.loadRecentActivity();
    this.loadProfileAssignments();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  protected get displayName(): string {
    const claims = this.authService.getTokenClaims();
    if (claims?.name?.trim()) {
      return claims.name.trim();
    }

    if (this.currentParticipant?.name?.trim()) {
      return this.currentParticipant.name.trim();
    }

    if (claims?.preferred_username?.trim()) {
      return claims.preferred_username.trim();
    }

    return 'Workspace User';
  }

  protected get roleLabel(): string {
    if (this.currentParticipant?.role?.trim()) {
      return this.currentParticipant.role.trim();
    }

    const roleFromClaims = this.humanRoleFromClaims(this.authService.roles());
    if (roleFromClaims) {
      return roleFromClaims;
    }

    return 'Workspace User';
  }

  protected get avatarUrl(): string {
    return this.currentParticipant?.avatarUrl?.trim() || WorkspaceProfileComponent.DEFAULT_AVATAR_URL;
  }

  protected get avatarAlt(): string {
    return `${this.displayName} profile picture`;
  }

  protected get generalInfoFields(): ProfileInfoField[] {
    const claims = this.authService.getTokenClaims();

    return [
      this.infoField('Full name', this.displayName),
      this.infoField('Email', claims?.email),
      this.infoField('Role', this.roleLabel),
      this.infoField('Club / Team', claims?.fos_club_id),
      this.infoField('Phone', null),
      this.infoField('Position / Job title', this.currentParticipant?.role ?? null),
      this.infoField('Joined date', null)
    ];
  }

  protected formatDueLabel(value: string | null): string {
    if (!value) {
      return 'Not provided';
    }

    const parsedDate = new Date(value);
    if (Number.isNaN(parsedDate.getTime())) {
      return 'Not provided';
    }

    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    }).format(parsedDate);
  }

  protected taskDescriptionPreview(task: ProfileTaskItem): string {
    const text = task.description.trim();
    if (!text) {
      return 'No description provided.';
    }

    return text.length > 140 ? `${text.slice(0, 137)}...` : text;
  }

  private get currentParticipant(): EventParticipant | null {
    const participants = this.workspaceData.getParticipants();
    const claims = this.authService.getTokenClaims();

    if (claims?.sub) {
      const byId = participants.find((person) => person.id === claims.sub);
      if (byId) {
        return byId;
      }
    }

    if (claims?.name?.trim()) {
      const normalizedClaimName = claims.name.trim().toLowerCase();
      const byName = participants.find((person) => person.name.trim().toLowerCase().includes(normalizedClaimName));
      if (byName) {
        return byName;
      }
    }

    return participants.find((person) => person.group === 'Staff' && person.role.toLowerCase().includes('head coach')) ?? participants[0] ?? null;
  }

  private loadRecentActivity(): void {
    const notifications = this.workspaceData.getChannelNotifications('notifications');
    const inbox = this.workspaceData.getChannelNotifications('inbox');

    this.recentActivity = [...notifications, ...inbox].slice(0, 3);
  }

  private loadProfileAssignments(): void {
    this.isLoadingAssignments = true;
    this.assignmentLoadError = '';

    const actorId = this.authService.currentActorId() ?? this.currentParticipant?.id ?? WorkspaceProfileComponent.FALLBACK_ACTOR_ID;

    this.subscriptions.add(
      this.calendarApi.listEvents().subscribe({
        next: (events) => {
          this.assignedTasks = this.mapAssignedTasks(events, actorId);
          this.missingDocuments = this.mergeMissingDocuments(this.mapMissingDocuments(events, actorId), this.mapLocalMissingDocuments());
          this.isLoadingAssignments = false;
        },
        error: () => {
          this.assignedTasks = [];
          this.missingDocuments = this.mapLocalMissingDocuments();
          this.assignmentLoadError = 'Unable to load live assignments right now.';
          this.isLoadingAssignments = false;
        }
      })
    );
  }

  private mapAssignedTasks(events: WorkspaceCalendarApiEvent[], actorId: string): ProfileTaskItem[] {
    const tasks: ProfileTaskItem[] = [];

    for (const eventItem of events) {
      for (const task of eventItem.tasks) {
        if (!this.isAssignedToActor(task, actorId)) {
          continue;
        }

        const dueAt = task.dueAt ?? eventItem.startAt;
        tasks.push({
          id: task.taskId,
          title: task.title.trim() || 'Untitled task',
          description: task.description ?? '',
          relatedSession: eventItem.title || null,
          dueAt,
          status: this.resolveTaskStatus(task, dueAt),
          assignedBy: eventItem.createdByActorId
        });
      }
    }

    return tasks.sort((left, right) => this.sortByDateValue(left.dueAt, right.dueAt));
  }

  private mapMissingDocuments(events: WorkspaceCalendarApiEvent[], actorId: string): ProfileMissingDocumentItem[] {
    const items: ProfileMissingDocumentItem[] = [];

    for (const eventItem of events) {
      for (const document of eventItem.requiredDocuments) {
        if (document.submitted || document.assignedToActorId !== actorId) {
          continue;
        }

        items.push({
          id: document.requirementId,
          name: document.description?.trim() || 'Required document',
          relatedSession: eventItem.title || null,
          dueAt: eventItem.startAt,
          assignedBy: eventItem.createdByActorId,
          status: 'Missing'
        });
      }
    }

    return items.sort((left, right) => this.sortByDateValue(left.dueAt, right.dueAt));
  }

  private mapLocalMissingDocuments(): ProfileMissingDocumentItem[] {
    const currentParticipantId = this.currentParticipant?.id ?? '';
    if (!currentParticipantId) {
      return [];
    }

    return this.workspaceData
      .getCalendarEvents()
      .flatMap((eventItem) =>
        eventItem.requiredDocuments
          .filter((document) => !document.uploaded && document.responsibleStaffId === currentParticipantId)
          .map((document) => ({
            id: `local-${eventItem.id}-${document.id}`,
            name: document.name,
            relatedSession: eventItem.title,
            dueAt: eventItem.date,
            assignedBy: eventItem.coachName,
            status: 'Missing' as const
          }))
      );
  }

  private mergeMissingDocuments(
    apiItems: ProfileMissingDocumentItem[],
    localItems: ProfileMissingDocumentItem[]
  ): ProfileMissingDocumentItem[] {
    if (apiItems.length === 0) {
      return localItems;
    }

    const merged = [...apiItems];
    const seenKeys = new Set(apiItems.map((item) => `${item.name}|${item.relatedSession ?? ''}`));

    for (const item of localItems) {
      const key = `${item.name}|${item.relatedSession ?? ''}`;
      if (!seenKeys.has(key)) {
        merged.push(item);
      }
    }

    return merged.sort((left, right) => this.sortByDateValue(left.dueAt, right.dueAt));
  }

  private isAssignedToActor(task: WorkspaceCalendarApiTask, actorId: string): boolean {
    return !!task.assignedToActorId && task.assignedToActorId === actorId;
  }

  private resolveTaskStatus(task: WorkspaceCalendarApiTask, dueAt: string | null): TaskStatus {
    if (task.completed) {
      return 'Done';
    }

    if (!dueAt) {
      return 'Pending';
    }

    const dueDate = new Date(dueAt);
    if (Number.isNaN(dueDate.getTime())) {
      return 'Pending';
    }

    return dueDate.getTime() < Date.now() ? 'Overdue' : 'Pending';
  }

  private sortByDateValue(left: string | null, right: string | null): number {
    const leftDate = left ? new Date(left) : null;
    const rightDate = right ? new Date(right) : null;

    const leftTime = leftDate && !Number.isNaN(leftDate.getTime()) ? leftDate.getTime() : Number.MAX_SAFE_INTEGER;
    const rightTime = rightDate && !Number.isNaN(rightDate.getTime()) ? rightDate.getTime() : Number.MAX_SAFE_INTEGER;

    return leftTime - rightTime;
  }

  private infoField(label: string, value: string | null | undefined): ProfileInfoField {
    const trimmed = value?.trim();
    return {
      label,
      value: trimmed || 'Not provided',
      provided: !!trimmed
    };
  }

  private humanRoleFromClaims(roles: string[]): string | null {
    const role = roles[0];
    if (!role) {
      return null;
    }

    const dictionary: Record<string, string> = {
      HEAD_COACH: 'Head Coach',
      STAFF: 'Staff',
      PLAYER: 'Player',
      ADMIN: 'Admin',
      CLUB_ADMIN: 'Admin'
    };

    if (dictionary[role]) {
      return dictionary[role];
    }

    return role
      .split('_')
      .filter((segment) => !!segment)
      .map((segment) => segment.charAt(0) + segment.slice(1).toLowerCase())
      .join(' ');
  }
}
