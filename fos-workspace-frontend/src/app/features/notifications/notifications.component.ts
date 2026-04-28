import { Component } from '@angular/core';
import { WorkspaceDataService } from '../../core/data/workspace-data.service';
import { WorkspaceNotification } from '../../shared/models/notification.model';
import { NotificationItemComponent } from '../../shared/notification-item/notification-item.component';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [NotificationItemComponent],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss'
})
export class NotificationsComponent {
  constructor(private readonly workspaceData: WorkspaceDataService) {}

  protected get items(): WorkspaceNotification[] {
    return this.workspaceData.getChannelNotifications('notifications');
  }

  protected markRead(item: WorkspaceNotification): void {
    this.workspaceData.markNotificationRead(item.id);
  }

  protected markAllRead(): void {
    this.workspaceData.markAllAsRead('notifications');
  }

}
