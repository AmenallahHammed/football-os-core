import { Injectable, computed, signal } from '@angular/core';
import { AuthConfig, OAuthService } from 'angular-oauth2-oidc';

import { environment } from '../../../environments/environment';

export interface AuthTokenClaims {
  sub: string;
  roles: string[];
  fos_club_id: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly authenticatedState = signal(false);
  private readonly tokenClaimsState = signal<AuthTokenClaims | null>(null);

  readonly authenticated = computed(() => this.authenticatedState());
  readonly tokenClaims = computed(() => this.tokenClaimsState());

  constructor(private readonly oauthService: OAuthService) {
    if (!environment.auth.enabled) {
      this.authenticatedState.set(true);
      return;
    }

    this.oauthService.configure(this.authConfig);
    this.oauthService.setStorage(localStorage);
    this.oauthService.events.subscribe(() => this.syncAuthState());
  }

  async initializeAuth(): Promise<void> {
    if (!environment.auth.enabled) {
      this.authenticatedState.set(true);
      this.tokenClaimsState.set(null);
      return;
    }

    try {
      await this.oauthService.loadDiscoveryDocumentAndTryLogin();
    } catch (error) {
      console.error('OIDC initialization failed.', error);
    }

    this.syncAuthState();
  }

  login(redirectUrl?: string): void {
    if (!environment.auth.enabled) {
      return;
    }

    const targetUrl = redirectUrl ?? `${window.location.pathname}${window.location.search}${window.location.hash}`;
    this.oauthService.initCodeFlow(targetUrl);
  }

  logout(): void {
    if (!environment.auth.enabled) {
      this.authenticatedState.set(true);
      this.tokenClaimsState.set(null);
      return;
    }

    this.oauthService.logOut();
    this.syncAuthState();
  }

  isAuthenticated(): boolean {
    if (!environment.auth.enabled) {
      return true;
    }

    return this.authenticatedState();
  }

  getAccessToken(): string | null {
    if (!environment.auth.enabled) {
      return null;
    }

    const token = this.oauthService.getAccessToken();
    return token || null;
  }

  getTokenClaims(): AuthTokenClaims | null {
    return this.tokenClaimsState();
  }

  private syncAuthState(): void {
    if (!environment.auth.enabled) {
      this.authenticatedState.set(true);
      this.tokenClaimsState.set(null);
      return;
    }

    this.authenticatedState.set(this.oauthService.hasValidAccessToken());
    this.tokenClaimsState.set(this.readAccessTokenClaims());
  }

  private readAccessTokenClaims(): AuthTokenClaims | null {
    const accessToken = this.oauthService.getAccessToken();
    if (!accessToken) {
      return null;
    }

    const payload = this.decodeJwtPayload(accessToken);
    const subject = payload?.['sub'];
    if (!payload || typeof subject !== 'string') {
      return null;
    }

    const roleClaims = payload['roles'];
    const roles = Array.isArray(roleClaims)
      ? roleClaims.filter((role): role is string => typeof role === 'string')
      : [];

    const clubId = payload['fos_club_id'];

    return {
      sub: subject,
      roles,
      fos_club_id: typeof clubId === 'string' ? clubId : null
    };
  }

  private decodeJwtPayload(token: string): Record<string, unknown> | null {
    const segments = token.split('.');
    if (segments.length < 2) {
      return null;
    }

    try {
      const base64 = segments[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64.padEnd(base64.length + (4 - (base64.length % 4)) % 4, '=');
      const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
      return JSON.parse(new TextDecoder().decode(bytes)) as Record<string, unknown>;
    } catch {
      return null;
    }
  }

  private get authConfig(): AuthConfig {
    return {
      issuer: `${this.normalizedKeycloakUrl}/realms/${environment.auth.realm}`,
      redirectUri: window.location.origin,
      clientId: environment.auth.clientId,
      responseType: 'code',
      scope: 'openid profile email',
      requireHttps: false,
      strictDiscoveryDocumentValidation: false,
      showDebugInformation: false
    };
  }

  private get normalizedKeycloakUrl(): string {
    return environment.auth.keycloakUrl.replace(/\/+$/, '');
  }
}
