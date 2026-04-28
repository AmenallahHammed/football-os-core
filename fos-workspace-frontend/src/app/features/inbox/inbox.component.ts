import { Component } from '@angular/core';
import { WorkspaceDataService } from '../../core/data/workspace-data.service';
import { WorkspaceNotification } from '../../shared/models/notification.model';
import { NotificationItemComponent } from '../../shared/notification-item/notification-item.component';

@Component({
  selector: 'app-inbox',
  standalone: true,
  imports: [NotificationItemComponent],
  templateUrl: './inbox.component.html',
  styleUrl: './inbox.component.scss'
})
export class InboxComponent {
  constructor(private readonly workspaceData: WorkspaceDataService) {}

  protected get items(): WorkspaceNotification[] {
    return this.workspaceData.getChannelNotifications('inbox');
  }

  protected markRead(item: WorkspaceNotification): void {
    this.workspaceData.markNotificationRead(item.id);
  }

  protected markAllRead(): void {
    this.workspaceData.markAllAsRead('inbox');
  }

}
