import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, UrlTree } from '@angular/router';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class PublicAuthGuard implements CanActivate {
  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  canActivate(route: ActivatedRouteSnapshot): boolean | UrlTree {
    if (!environment.auth.enabled || !this.authService.isAuthenticated()) {
      return true;
    }

    return this.router.parseUrl(this.authService.getSafeReturnUrl(route.queryParamMap.get('returnUrl')));
  }
}
