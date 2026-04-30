import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ContextMenuAction, ContextMenuComponent } from '../../../shared/context-menu/context-menu.component';
import { CalendarEvent, RequiredEventDocument } from '../../../shared/models/event.model';

interface DocumentTarget {
  eventId: string;
  document: RequiredEventDocument;
}

@Component({
  selector: 'app-day-panel',
  standalone: true,
  imports: [DatePipe, FormsModule, ContextMenuComponent],
  templateUrl: './day-panel.component.html',
  styleUrl: './day-panel.component.scss'
})
export class DayPanelComponent {
  @Input({ required: true }) date = new Date();
  @Input() events: CalendarEvent[] = [];

  @Output() closed = new EventEmitter<void>();
  @Output() addEventRequested = new EventEmitter<void>();
  @Output() documentUploaded = new EventEmitter<{ eventId: string; documentId: string }>();
  @Output() documentRenamed = new EventEmitter<{ eventId: string; documentId: string; name: string }>();
  @Output() documentDeleted = new EventEmitter<{ eventId: string; documentId: string }>();

  protected readonly documentActions: ContextMenuAction[] = [
    { id: 'open', label: 'Open' },
    { id: 'download', label: 'Download' },
    { id: 'share', label: 'Share' },
    { id: 'rename', label: 'Rename' },
    { id: 'delete', label: 'Delete' }
  ];

  protected contextVisible = false;
  protected contextX = 0;
  protected contextY = 0;
  protected contextTarget: DocumentTarget | null = null;

  protected noteText = '';

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

  protected get missingDocumentCount(): number {
    return this.events.reduce(
      (count, event) => count + event.requiredDocuments.filter((document) => !document.uploaded).length,
      0
    );
  }

  protected closePanel(): void {
    this.closed.emit();
  }

  protected requestAddEvent(): void {
    this.addEventRequested.emit();
  }

  protected uploadMissingDocument(event: CalendarEvent): void {
    const missingDocument = event.requiredDocuments.find((document) => !document.uploaded);
    if (!missingDocument) {
      return;
    }

    this.documentUploaded.emit({ eventId: event.id, documentId: missingDocument.id });
  }

  protected openDocumentMenu(mouseEvent: MouseEvent, eventId: string, document: RequiredEventDocument): void {
    mouseEvent.preventDefault();
    this.contextTarget = { eventId, document };
    this.contextX = mouseEvent.clientX;
    this.contextY = mouseEvent.clientY;
    this.contextVisible = true;
  }

  protected handleDocumentAction(action: ContextMenuAction): void {
    if (!this.contextTarget) {
      return;
    }

    const { eventId, document } = this.contextTarget;

    if (action.id === 'rename') {
      const nextName = window.prompt('Rename document', document.name);
      if (nextName?.trim()) {
        this.documentRenamed.emit({ eventId, documentId: document.id, name: nextName.trim() });
      }
    }

    if (action.id === 'delete') {
      this.documentDeleted.emit({ eventId, documentId: document.id });
    }

    this.closeContextMenu();
  }

  protected closeContextMenu(): void {
    this.contextVisible = false;
  }

  protected attendeeOverflow(event: CalendarEvent): number {
    return Math.max(0, event.attendees.length - 4);
  }

  protected eventMeta(event: CalendarEvent): string {
    if (event.opponent) {
      return `${event.location} - vs ${event.opponent}`;
    }

    return event.location;
  }

  protected submitNote(): void {
    if (!this.noteText.trim()) {
      return;
    }

    this.noteText = '';
  }
}
