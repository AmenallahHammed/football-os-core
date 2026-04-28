import { Component, EventEmitter, Input, Output } from '@angular/core';
import { WorkspaceDocument } from '../models/document.model';

@Component({
  selector: 'app-onlyoffice-editor',
  standalone: true,
  imports: [],
  templateUrl: './onlyoffice-editor.component.html',
  styleUrl: './onlyoffice-editor.component.scss'
})
export class OnlyofficeEditorComponent {
  @Input() document: WorkspaceDocument | null = null;
  @Output() closed = new EventEmitter<void>();

  protected closeEditor(): void {
    this.closed.emit();
  }

}
