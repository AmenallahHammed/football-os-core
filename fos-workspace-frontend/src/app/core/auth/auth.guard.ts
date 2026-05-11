import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, CanActivateChild, Router, RouterStateSnapshot, UrlTree } from '@angular/router';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate, CanActivateChild {
  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  canActivate(_route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree {
    return this.ensureAuthenticated(state.url);
  }

  canActivateChild(_childRoute: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree {
    return this.ensureAuthenticated(state.url);
  }

  private ensureAuthenticated(redirectUrl: string): boolean | UrlTree {
    if (!environment.auth.enabled) {
      return true;
    }

    if (this.authService.isAuthenticated()) {
      return true;
    }

    const returnUrl = this.authService.getSafeReturnUrl(redirectUrl);
    return this.router.createUrlTree(['/login'], {
      queryParams: {
        returnUrl
      }
    });
  }
}
