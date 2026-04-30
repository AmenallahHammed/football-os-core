import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  EventCreateRequest,
  EventParticipant,
  EventTask,
  ParticipantGroup,
  RequiredEventDocument
} from '../../../shared/models/event.model';

interface RequiredDocumentDraft {
  id: string;
  name: string;
  responsibleStaffId: string;
}

interface AttendeeFilter {
  label: string;
  value: 'All' | ParticipantGroup;
}

@Component({
  selector: 'app-event-create',
  standalone: true,
  imports: [FormsModule, DatePipe],
  templateUrl: './event-create.component.html',
  styleUrl: './event-create.component.scss'
})
export class EventCreateComponent {
  @Input({ required: true }) date = new Date();
  @Input() participants: EventParticipant[] = [];

  @Output() canceled = new EventEmitter<void>();
  @Output() created = new EventEmitter<EventCreateRequest>();

  protected eventName = '';
  protected usageQuery = '';
  protected selectedUsage = '';
  protected usageListOpen = false;

  protected attendeeSearch = '';
  protected attendeeFilter: 'All' | ParticipantGroup = 'All';
  protected readonly attendeeFilters: AttendeeFilter[] = [
    { label: 'All', value: 'All' },
    { label: 'Players', value: 'Player' },
    { label: 'Medical Staff', value: 'Medical Staff' },
    { label: 'Admin Staff', value: 'Admin Staff' },
    { label: 'Staff', value: 'Staff' }
  ];

  protected selectedAttendees: EventParticipant[] = [];
  protected requiredDocuments: RequiredDocumentDraft[] = [this.newDocumentDraft()];

  protected taskDescription = '';
  protected taskAssigneeIds: string[] = [];
  protected tasks: EventTask[] = [];

  private readonly usageOptions = [
    'Pitch A',
    'Pitch B',
    'Indoor Hall',
    'Video Analysis Room',
    'Medical Center',
    'Board Room'
  ];

  protected get filteredUsageOptions(): string[] {
    const query = this.usageQuery.trim().toLowerCase();
    return this.usageOptions.filter((option) => option.toLowerCase().includes(query));
  }

  protected get filteredParticipants(): EventParticipant[] {
    const query = this.attendeeSearch.trim().toLowerCase();

    return this.participants
      .filter((participant) => this.attendeeFilter === 'All' || participant.group === this.attendeeFilter)
      .filter((participant) => {
        if (!query) {
          return true;
        }

        return (
          participant.name.toLowerCase().includes(query) ||
          participant.role.toLowerCase().includes(query) ||
          participant.group.toLowerCase().includes(query)
        );
      });
  }

  protected openUsageList(): void {
    this.usageListOpen = true;
  }

  protected onUsageInput(): void {
    this.selectedUsage = '';
    this.usageListOpen = true;
  }

  protected chooseUsage(option: string): void {
    this.selectedUsage = option;
    this.usageQuery = option;
    this.usageListOpen = false;
  }

  protected setAttendeeFilter(filter: 'All' | ParticipantGroup): void {
    this.attendeeFilter = filter;
  }

  protected toggleAttendee(participant: EventParticipant): void {
    if (this.selectedAttendees.some((selected) => selected.id === participant.id)) {
      this.selectedAttendees = this.selectedAttendees.filter((selected) => selected.id !== participant.id);
      return;
    }

    this.selectedAttendees = [...this.selectedAttendees, participant];
  }

  protected removeAttendee(participantId: string): void {
    this.selectedAttendees = this.selectedAttendees.filter((selected) => selected.id !== participantId);
  }

  protected isAttendeeSelected(participantId: string): boolean {
    return this.selectedAttendees.some((selected) => selected.id === participantId);
  }

  protected addDocumentRow(): void {
    this.requiredDocuments = [...this.requiredDocuments, this.newDocumentDraft()];
  }

  protected removeDocumentRow(rowId: string): void {
    if (this.requiredDocuments.length === 1) {
      this.requiredDocuments = [this.newDocumentDraft()];
      return;
    }

    this.requiredDocuments = this.requiredDocuments.filter((row) => row.id !== rowId);
  }

  protected toggleTaskAssignee(participantId: string): void {
    if (this.taskAssigneeIds.includes(participantId)) {
      this.taskAssigneeIds = this.taskAssigneeIds.filter((id) => id !== participantId);
      return;
    }

    this.taskAssigneeIds = [...this.taskAssigneeIds, participantId];
  }

  protected isTaskAssigneeSelected(participantId: string): boolean {
    return this.taskAssigneeIds.includes(participantId);
  }

  protected addTask(): void {
    const trimmed = this.taskDescription.trim();
    if (!trimmed) {
      return;
    }

    const nextTask: EventTask = {
      id: this.newId('task'),
      description: trimmed,
      assigneeIds: [...this.taskAssigneeIds]
    };

    this.tasks = [...this.tasks, nextTask];
    this.taskDescription = '';
    this.taskAssigneeIds = [];
  }

  protected removeTask(taskId: string): void {
    this.tasks = this.tasks.filter((task) => task.id !== taskId);
  }

  protected cancel(): void {
    this.canceled.emit();
  }

  protected createEvent(): void {
    if (!this.eventName.trim()) {
      return;
    }

    const requiredDocuments: RequiredEventDocument[] = this.requiredDocuments
      .filter((row) => row.name.trim())
      .map((row) => {
        const responsible = this.participants.find((participant) => participant.id === row.responsibleStaffId);
        return {
          id: row.id,
          name: row.name.trim(),
          responsibleStaffId: row.responsibleStaffId,
          responsibleStaffName: responsible?.name ?? 'Unassigned',
          uploaded: false
        };
      });

    const payload: EventCreateRequest = {
      date: this.toIsoDate(this.date),
      title: this.eventName.trim(),
      usage: this.selectedUsage || this.usageQuery.trim(),
      attendees: this.selectedAttendees,
      requiredDocuments,
      tasks: this.tasks
    };

    this.created.emit(payload);
  }

  protected participantById(participantId: string): EventParticipant | null {
    return this.participants.find((participant) => participant.id === participantId) ?? null;
  }

  private newDocumentDraft(): RequiredDocumentDraft {
    return {
      id: this.newId('req'),
      name: '',
      responsibleStaffId: ''
    };
  }

  private toIsoDate(date: Date): string {
    return date.toISOString().slice(0, 10);
  }

  private newId(prefix: string): string {
    return `${prefix}-${Math.random().toString(36).slice(2, 9)}`;
  }
}
