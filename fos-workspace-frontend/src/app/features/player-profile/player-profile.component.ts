import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { WorkspaceDataService } from '../../core/data/workspace-data.service';
import { PlayerProfile } from '../../shared/models/player.model';
import { TabOption, TabsComponent } from '../../shared/tabs/tabs.component';

@Component({
  selector: 'app-player-profile',
  standalone: true,
  imports: [RouterLink, TabsComponent],
  templateUrl: './player-profile.component.html',
  styleUrl: './player-profile.component.scss'
})
export class PlayerProfileComponent implements OnInit {
  protected player: PlayerProfile | null = null;
  protected activeTab = 'documents';
  protected readonly tabs: TabOption[] = [
    { id: 'documents', label: 'Documents' },
    { id: 'events', label: 'Events' }
  ];

  constructor(
    private readonly route: ActivatedRoute,
    private readonly workspaceData: WorkspaceDataService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const playerId = params.get('id');
      this.player = playerId ? this.workspaceData.getPlayerById(playerId) : null;
    });
  }

  protected onTabChanged(tabId: string): void {
    this.activeTab = tabId;
  }

}
