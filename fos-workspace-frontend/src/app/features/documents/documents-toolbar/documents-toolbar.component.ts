import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-documents-toolbar',
  standalone: true,
  imports: [],
  templateUrl: './documents-toolbar.component.html',
  styleUrl: './documents-toolbar.component.scss'
})
export class DocumentsToolbarComponent {
  @Input() searchTerm = '';

  @Output() searchTermChange = new EventEmitter<string>();
  @Output() uploadRequested = new EventEmitter<void>();
  @Output() createRequested = new EventEmitter<void>();
  @Output() organizeRequested = new EventEmitter<void>();
  @Output() overflowRequested = new EventEmitter<void>();
  @Output() viewRequested = new EventEmitter<void>();

  protected onSearchInput(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.searchTermChange.emit(input?.value ?? '');
  }

  protected requestUpload(): void {
    this.uploadRequested.emit();
  }

  protected requestCreate(): void {
    this.createRequested.emit();
  }

  protected requestOrganize(): void {
    this.organizeRequested.emit();
  }

  protected requestOverflow(): void {
    this.overflowRequested.emit();
  }

  protected requestListView(): void {
    this.viewRequested.emit();
  }
}
