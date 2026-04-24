import { animate, state, style, transition, trigger } from '@angular/animations';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { PlayerProfile } from '../../shared/models/player.model';

@Component({
  selector: 'app-player-card',
  standalone: true,
  imports: [],
  templateUrl: './player-card.component.html',
  styleUrl: './player-card.component.scss',
  animations: [
    trigger('hoverLift', [
      state(
        'rest',
        style({
          transform: 'translateY(0)',
          boxShadow: '0 0 0 rgba(59,130,246,0)'
        })
      ),
      state(
        'hover',
        style({
          transform: 'translateY(-6px)',
          boxShadow: '0 10px 20px rgba(59,130,246,0.25)'
        })
      ),
      transition('rest <=> hover', animate('180ms ease-out'))
    ])
  ]
})
export class PlayerCardComponent {
  @Input({ required: true }) player!: PlayerProfile;
  @Output() selected = new EventEmitter<PlayerProfile>();

  protected animationState: 'rest' | 'hover' = 'rest';

  protected onHover(active: boolean): void {
    this.animationState = active ? 'hover' : 'rest';
  }

  protected selectCard(): void {
    this.selected.emit(this.player);
  }

}
