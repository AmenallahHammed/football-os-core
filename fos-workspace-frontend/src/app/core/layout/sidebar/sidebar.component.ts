import { Component } from '@angular/core';
import { WorkspaceDataService } from '../../data/workspace-data.service';
import { NavItemComponent } from '../nav-item/nav-item.component';
import { UserProfileComponent } from '../user-profile/user-profile.component';

interface SidebarItem {
  label: string;
  route: string;
  icon: string;
  exact: boolean;
  badge?: number;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [NavItemComponent, UserProfileComponent],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent {
  constructor(private readonly workspaceData: WorkspaceDataService) {}

  protected get navItems(): SidebarItem[] {
    return [
      { label: 'Home', route: '/home', icon: 'H', exact: true },
      { label: 'Documents', route: '/documents', icon: 'D', exact: false },
      { label: 'Players', route: '/players', icon: 'P', exact: false },
      {
        label: 'Notifications',
        route: '/notifications',
        icon: 'N',
        exact: false,
        badge: this.workspaceData.unreadNotificationCount()
      },
      {
        label: 'Inbox',
        route: '/inbox',
        icon: 'I',
        exact: false,
        badge: this.workspaceData.unreadInboxCount()
      },
      { label: 'Settings', route: '/settings', icon: 'S', exact: false }
    ];
  }

}
