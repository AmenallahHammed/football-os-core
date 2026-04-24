import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-dropzone',
  standalone: true,
  imports: [],
  templateUrl: './dropzone.component.html',
  styleUrl: './dropzone.component.scss'
})
export class DropzoneComponent {
  @Input() compact = false;
  @Output() filesSelected = new EventEmitter<File[]>();

  protected dragActive = false;

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = true;
  }

  protected onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = false;
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = false;
    const files = Array.from(event.dataTransfer?.files ?? []);
    this.filesSelected.emit(files);
  }

  protected onFileInput(event: Event): void {
    const target = event.target as HTMLInputElement;
    const files = Array.from(target.files ?? []);
    this.filesSelected.emit(files);
    target.value = '';
  }

}
