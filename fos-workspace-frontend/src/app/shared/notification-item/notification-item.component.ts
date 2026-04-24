import { Component, EventEmitter, Input, Output } from '@angular/core';
import { WorkspaceNotification } from '../models/notification.model';

@Component({
  selector: 'app-notification-item',
  standalone: true,
  imports: [],
  templateUrl: './notification-item.component.html',
  styleUrl: './notification-item.component.scss'
})
export class NotificationItemComponent {
  @Input({ required: true }) item!: WorkspaceNotification;
  @Output() selected = new EventEmitter<WorkspaceNotification>();

  protected selectItem(): void {
    this.selected.emit(this.item);
  }

}
