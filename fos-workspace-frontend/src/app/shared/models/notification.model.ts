export type NotificationChannel = 'notifications' | 'inbox';

export interface WorkspaceNotification {
  id: string;
  action: string;
  resource: string;
  timestamp: string;
  unread: boolean;
  channel: NotificationChannel;
}
