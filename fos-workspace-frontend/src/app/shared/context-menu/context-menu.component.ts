import { Component, EventEmitter, Input, Output } from '@angular/core';

export interface ContextMenuAction {
  id: string;
  label: string;
}

@Component({
  selector: 'app-context-menu',
  standalone: true,
  imports: [],
  templateUrl: './context-menu.component.html',
  styleUrl: './context-menu.component.scss'
})
export class ContextMenuComponent {
  @Input() visible = false;
  @Input() x = 0;
  @Input() y = 0;
  @Input() actions: ContextMenuAction[] = [];
  @Output() closed = new EventEmitter<void>();
  @Output() actionSelected = new EventEmitter<ContextMenuAction>();

  protected close(): void {
    this.closed.emit();
  }

  protected onAction(action: ContextMenuAction): void {
    this.actionSelected.emit(action);
  }

}
