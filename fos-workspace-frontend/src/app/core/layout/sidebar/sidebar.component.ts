import { Component } from '@angular/core';
import { WorkspaceRailComponent } from '../workspace-rail/workspace-rail.component';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [WorkspaceRailComponent],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss'
})
export class SidebarComponent {}
