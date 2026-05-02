import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, CanActivateChild, RouterStateSnapshot } from '@angular/router';

import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate, CanActivateChild {
  constructor(private readonly authService: AuthService) {}

  canActivate(_route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    return this.ensureAuthenticated(state.url);
  }

  canActivateChild(_childRoute: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    return this.ensureAuthenticated(state.url);
  }

  private ensureAuthenticated(redirectUrl: string): boolean {
    if (this.authService.isAuthenticated()) {
      return true;
    }

    this.authService.login(redirectUrl);
    return false;
  }
}
