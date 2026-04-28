import { Component, Input } from '@angular/core';
import { DocumentStatus } from '../models/document.model';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [],
  templateUrl: './status-badge.component.html',
  styleUrl: './status-badge.component.scss'
})
export class StatusBadgeComponent {
  @Input({ required: true }) status: DocumentStatus = 'Draft';

  protected get toneClass(): string {
    if (this.status === 'Active') {
      return 'active';
    }
    if (this.status === 'Archived') {
      return 'archived';
    }
    return 'draft';
  }

}
