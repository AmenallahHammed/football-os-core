import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { RouterLink } from '@angular/router';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './login-page.component.html',
  styleUrl: './login-page.component.scss'
})
export class LoginPageComponent implements OnInit {
  protected isSigningIn = false;
  protected signInError = '';
  protected statusMessage = 'Secure sign-in opens through Football OS identity.';

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  ngOnInit(): void {
    if (environment.auth.enabled && this.authService.isAuthenticated()) {
      void this.router.navigateByUrl(this.authService.getSafeReturnUrl(this.returnUrl));
    }
  }

  onSignIn(): void {
    this.isSigningIn = true;
    this.signInError = '';
    this.statusMessage = 'Opening secure sign-in...';

    try {
      if (environment.auth.enabled) {
        this.authService.login(this.returnUrl);
        return;
      }

      void this.router.navigateByUrl(this.authService.getSafeReturnUrl(this.returnUrl));
    } catch {
      this.isSigningIn = false;
      this.signInError = 'Secure sign-in could not be opened. Please check the identity service and try again.';
      this.statusMessage = '';
    }
  }

  private get returnUrl(): string {
    return this.authService.getSafeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
  }
}
