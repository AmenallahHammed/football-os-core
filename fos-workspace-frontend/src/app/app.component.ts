import { Component } from '@angular/core';
import { animate, query, style, transition, trigger } from '@angular/animations';
import { ActivatedRoute, Data, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
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
            animate('120ms ease', style({ opacity: 0, transform: 'translateY(-4px)' }))
          ],
          { optional: true }
        ),
        query(':enter', [style({ opacity: 0, transform: 'translateY(4px)' })], { optional: true }),
        query(':enter', [animate('160ms ease-out', style({ opacity: 1, transform: 'translateY(0)' }))], { optional: true })
      ])
    ])
  ]
})
export class AppComponent {
  protected routeAnimationState = 'default';
  protected fullScreenRoute = false;
  protected fullHeightRoute = false;

  constructor(
    private readonly router: Router,
    private readonly activatedRoute: ActivatedRoute
  ) {
    this.applyRouteLayout(this.deepestRouteData(this.activatedRoute));

    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
      this.applyRouteLayout(this.deepestRouteData(this.activatedRoute));
    });
  }

  protected onOutletActivated(outlet: RouterOutlet): void {
    this.applyRouteLayout(outlet.activatedRouteData ?? this.deepestRouteData(this.activatedRoute));
  }

  private deepestRouteData(route: ActivatedRoute): Data {
    let current: ActivatedRoute | null = route;
    while (current?.firstChild) {
      current = current.firstChild;
    }

    return current?.snapshot.data ?? {};
  }

  private applyRouteLayout(data: Data): void {
    this.routeAnimationState = (data['animation'] as string) ?? 'default';
    this.fullScreenRoute = data['fullScreen'] === true;
    this.fullHeightRoute = data['fullHeight'] === true;
  }
}
