import { Component, Input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { WorkspaceDataService } from '../../data/workspace-data.service';
import { NavItemComponent } from '../nav-item/nav-item.component';
import { UserProfileComponent } from '../user-profile/user-profile.component';
import { WorkspaceCalendarIconComponent } from '../../../shared/workspace-icon/workspace-icon.component';

interface WorkspaceRailItem {
  label: string;
  route: string;
  icon: string;
  exact: boolean;
  badge?: number;
}

@Component({
  selector: 'app-workspace-rail',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NavItemComponent, UserProfileComponent, WorkspaceCalendarIconComponent],
  templateUrl: './workspace-rail.component.html',
  styleUrl: './workspace-rail.component.scss'
})
export class WorkspaceRailComponent {
  @Input() showProfile = true;

  constructor(
    private readonly authService: AuthService,
    private readonly workspaceData: WorkspaceDataService
  ) {}

  protected get navItems(): WorkspaceRailItem[] {
    return [
      { label: 'Calendar', route: '/workspace/calendar', icon: 'calendar', exact: false },
      { label: 'Documents', route: '/workspace/documents', icon: 'folder', exact: false },
      { label: 'Players', route: '/workspace/players', icon: 'users', exact: false },
      {
        label: 'Notifications',
        route: '/workspace/notifications',
        icon: 'bell',
        exact: false,
        badge: this.workspaceData.unreadNotificationCount()
      },
      {
        label: 'Inbox',
        route: '/workspace/inbox',
        icon: 'inbox',
        exact: false,
        badge: this.workspaceData.unreadInboxCount()
      },
      { label: 'Settings', route: '/workspace/settings', icon: 'settings', exact: false }
    ];
  }

  protected logout(): void {
    this.authService.logout('/login');
  }
}
