import { Component, EventEmitter, Input, Output } from '@angular/core';

export interface TabOption {
  id: string;
  label: string;
}

@Component({
  selector: 'app-tabs',
  standalone: true,
  imports: [],
  templateUrl: './tabs.component.html',
  styleUrl: './tabs.component.scss'
})
export class TabsComponent {
  @Input({ required: true }) tabs: TabOption[] = [];
  @Input() activeTab = '';
  @Output() tabChanged = new EventEmitter<string>();

  protected select(tabId: string): void {
    if (this.activeTab === tabId) {
      return;
    }

    this.tabChanged.emit(tabId);
  }

}
