import { Component, EventEmitter, Input, Output } from '@angular/core';

export interface BreadcrumbSegment {
  id: string | null;
  label: string;
}

@Component({
  selector: 'app-breadcrumb',
  standalone: true,
  imports: [],
  templateUrl: './breadcrumb.component.html',
  styleUrl: './breadcrumb.component.scss'
})
export class BreadcrumbComponent {
  @Input({ required: true }) segments: BreadcrumbSegment[] = [];
  @Output() segmentSelected = new EventEmitter<BreadcrumbSegment>();

  protected select(segment: BreadcrumbSegment, isLast: boolean): void {
    if (isLast) {
      return;
    }

    this.segmentSelected.emit(segment);
  }

}
