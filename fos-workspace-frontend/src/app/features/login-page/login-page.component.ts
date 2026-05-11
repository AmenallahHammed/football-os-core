import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
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
  private readonly router = inject(Router);
  private readonly authService = environment.auth.enabled ? inject(AuthService) : null;

  ngOnInit(): void {
    // Keep the user on the login page. Auth flow starts only when they click "Sign In".
  }

  onSignIn(): void {
    if (environment.auth.enabled && this.authService) {
      this.authService.login('/workspace/calendar');
      return;
    }

    void this.router.navigateByUrl('/workspace/calendar');
  }
}
