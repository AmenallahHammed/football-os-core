import { Component, Input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { WorkspaceCalendarIconComponent } from '../../../shared/workspace-icon/workspace-icon.component';

@Component({
  selector: 'app-nav-item',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, WorkspaceCalendarIconComponent],
  templateUrl: './nav-item.component.html',
  styleUrl: './nav-item.component.scss'
})
export class NavItemComponent {
  @Input({ required: true }) label = '';
  @Input({ required: true }) route: string | string[] = '/';
  @Input() icon = '*';
  @Input() exact = false;
  @Input() active = false;
  @Input() badge: number | null = null;

}
