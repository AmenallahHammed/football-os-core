import { Component } from '@angular/core';
import { animate, query, style, transition, trigger } from '@angular/animations';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from './core/layout/sidebar/sidebar.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  animations: [
    trigger('routeTransition', [
      transition('* <=> *', [
        query(
          ':leave',
          [
            style({ opacity: 1, transform: 'translateY(0)' }),
            animate('120ms ease', style({ opacity: 0, transform: 'translateY(-10px)' }))
          ],
          { optional: true }
        ),
        query(':enter', [style({ opacity: 0, transform: 'translateY(14px)' })], { optional: true }),
        query(':enter', [animate('220ms 70ms ease-out', style({ opacity: 1, transform: 'translateY(0)' }))], {
          optional: true
        })
      ])
    ])
  ]
})
export class AppComponent {
  protected getRouteAnimationState(outlet: RouterOutlet): string {
    return (outlet.activatedRouteData['animation'] as string) ?? 'default';
  }
}
