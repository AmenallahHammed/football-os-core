import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { WorkspaceDataService } from '../../core/data/workspace-data.service';
import { PlayerProfile, PlayerPosition } from '../../shared/models/player.model';
import { PlayerCardComponent } from '../player-card/player-card.component';

@Component({
  selector: 'app-players',
  standalone: true,
  imports: [FormsModule, PlayerCardComponent],
  templateUrl: './players.component.html',
  styleUrl: './players.component.scss'
})
export class PlayersComponent {
  protected searchTerm = '';
  protected selectedPosition: PlayerPosition | 'ALL' = 'ALL';

  constructor(
    private readonly workspaceData: WorkspaceDataService,
    private readonly router: Router
  ) {}

  protected get players(): PlayerProfile[] {
    const query = this.searchTerm.trim().toLowerCase();

    return this.workspaceData
      .getPlayers()
      .filter((player) => this.selectedPosition === 'ALL' || player.position === this.selectedPosition)
      .filter((player) => !query || player.name.toLowerCase().includes(query));
  }

  protected openProfile(player: PlayerProfile): void {
    this.router.navigate(['/players', player.id]);
  }

}
