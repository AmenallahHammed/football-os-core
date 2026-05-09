import { APP_INITIALIZER, ApplicationConfig, importProvidersFrom } from '@angular/core';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { Routes, provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { OAuthModule } from 'angular-oauth2-oidc';
import { environment } from '../environments/environment';

import { authenticatedRoutes, publicRoutes, routes } from './app.routes';
import { AuthGuard } from './core/auth/auth.guard';
import { AuthInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';

const guardedRoutes: Routes = [
  ...publicRoutes,
  {
    path: '',
    canActivate: [AuthGuard],
    canActivateChild: [AuthGuard],
    children: authenticatedRoutes
  }
];

function initializeAuth(authService: AuthService): () => Promise<void> {
  return () => authService.initializeAuth();
}

const appRoutes = environment.auth.enabled ? guardedRoutes : routes;

const authProviders = environment.auth.enabled
  ? [
      importProvidersFrom(OAuthModule.forRoot()),
      {
        provide: APP_INITIALIZER,
        useFactory: initializeAuth,
        deps: [AuthService],
        multi: true
      },
      {
        provide: HTTP_INTERCEPTORS,
        useClass: AuthInterceptor,
        multi: true
      }
    ]
  : [];

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(appRoutes),
    provideAnimations(),
    provideHttpClient(withInterceptorsFromDi()),
    ...authProviders
  ]
};
