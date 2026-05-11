import { Injectable, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthEvent, OAuthService } from 'angular-oauth2-oidc';

import { environment } from '../../../environments/environment';
import { buildKeycloakAuthConfig } from './keycloak.config';

export interface AuthTokenClaims {
  sub: string;
  roles: string[];
  fos_club_id: string | null;
  email: string | null;
  name: string | null;
  preferred_username: string | null;
}

const RETURN_URL_STORAGE_KEY = 'fos.auth.returnUrl';
const DEFAULT_AUTHENTICATED_ROUTE = '/workspace/calendar';
const DEFAULT_LOGIN_ROUTE = '/login';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly authenticatedState = signal(false);
  private readonly tokenClaimsState = signal<AuthTokenClaims | null>(null);

  readonly authenticated = computed(() => this.authenticatedState());
  readonly tokenClaims = computed(() => this.tokenClaimsState());

  constructor(
    private readonly oauthService: OAuthService,
    private readonly router: Router
  ) {
    if (!environment.auth.enabled) {
      this.authenticatedState.set(true);
      return;
    }

    this.oauthService.configure(buildKeycloakAuthConfig());
    this.oauthService.setStorage(sessionStorage);
    this.oauthService.events.subscribe((event) => this.handleOAuthEvent(event));
  }

  async initializeAuth(): Promise<void> {
    if (!environment.auth.enabled) {
      this.authenticatedState.set(true);
      this.tokenClaimsState.set(null);
      return;
    }

    try {
      await this.oauthService.loadDiscoveryDocumentAndTryLogin({
        onTokenReceived: () => this.restoreSavedReturnUrl()
      });
      this.oauthService.setupAutomaticSilentRefresh({}, 'access_token', true);
    } catch (error) {
      console.error('OIDC initialization failed.', error);
    }

    this.syncAuthState();
    if (this.isAuthenticated()) {
      this.restoreSavedReturnUrl();
    }
  }

  login(redirectUrl?: string): void {
    if (!environment.auth.enabled) {
      return;
    }

    const targetUrl = this.getSafeReturnUrl(redirectUrl ?? this.currentBrowserPath());
    this.storeReturnUrl(targetUrl);
    this.oauthService.initCodeFlow(targetUrl);
  }

  logout(returnUrl = DEFAULT_LOGIN_ROUTE): void {
    if (!environment.auth.enabled) {
      this.authenticatedState.set(true);
      this.tokenClaimsState.set(null);
      void this.router.navigateByUrl(this.getSafeReturnUrl(returnUrl, DEFAULT_LOGIN_ROUTE));
      return;
    }

    sessionStorage.removeItem(RETURN_URL_STORAGE_KEY);
    this.authenticatedState.set(false);
    this.tokenClaimsState.set(null);
    this.oauthService.logOut({
      post_logout_redirect_uri: `${window.location.origin}${this.getSafeReturnUrl(returnUrl, DEFAULT_LOGIN_ROUTE)}`
    });
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

    if (!this.oauthService.hasValidAccessToken()) {
      this.syncAuthState();
      return null;
    }

    const token = this.oauthService.getAccessToken();
    return token || null;
  }

  getTokenClaims(): AuthTokenClaims | null {
    return this.tokenClaimsState();
  }

  async refreshAccessToken(): Promise<boolean> {
    if (!environment.auth.enabled) {
      return true;
    }

    if (!this.oauthService.getRefreshToken()) {
      this.syncAuthState();
      return false;
    }

    try {
      await this.oauthService.refreshToken();
      this.syncAuthState();
      return this.isAuthenticated();
    } catch (error) {
      console.error('OIDC token refresh failed.', error);
      this.syncAuthState();
      return false;
    }
  }

  roles(): string[] {
    return (this.tokenClaimsState()?.roles ?? []).map((role) => this.normalizeRole(role));
  }

  hasRole(role: string): boolean {
    return this.roles().includes(this.normalizeRole(role));
  }

  currentActorId(): string | null {
    return this.tokenClaimsState()?.sub ?? null;
  }

  currentClubId(): string | null {
    return this.tokenClaimsState()?.fos_club_id ?? null;
  }

  clearSession(): void {
    if (!environment.auth.enabled) {
      this.authenticatedState.set(true);
      this.tokenClaimsState.set(null);
      return;
    }

    sessionStorage.removeItem(RETURN_URL_STORAGE_KEY);
    this.oauthService.logOut(true);
    this.authenticatedState.set(false);
    this.tokenClaimsState.set(null);
  }

  handleUnauthorized(returnUrl?: string): void {
    if (!environment.auth.enabled) {
      return;
    }

    const targetUrl = this.getSafeReturnUrl(returnUrl ?? this.currentBrowserPath());
    this.clearSession();
    this.storeReturnUrl(targetUrl);
    void this.router.navigate(['/login'], {
      queryParams: {
        returnUrl: targetUrl
      }
    });
  }

  getSafeReturnUrl(returnUrl?: string | null, fallback = DEFAULT_AUTHENTICATED_ROUTE): string {
    const candidate = (returnUrl ?? '').trim();
    if (!candidate || !candidate.startsWith('/') || candidate.startsWith('//')) {
      return fallback;
    }

    if (candidate === DEFAULT_LOGIN_ROUTE || candidate.startsWith(`${DEFAULT_LOGIN_ROUTE}?`)) {
      return fallback;
    }

    return candidate;
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

    const clubId = payload['fos_club_id'];

    return {
      sub: subject,
      roles: this.extractRoles(payload),
      fos_club_id: typeof clubId === 'string' ? clubId : null,
      email: this.readStringClaim(payload, 'email'),
      name: this.readStringClaim(payload, 'name'),
      preferred_username: this.readStringClaim(payload, 'preferred_username')
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

  private extractRoles(payload: Record<string, unknown>): string[] {
    return [
      ...this.extractRoleArray(payload['roles']),
      ...this.extractRoleArray(this.readNestedClaim(payload, ['realm_access', 'roles'])),
      ...this.extractRoleArray(this.readNestedClaim(payload, ['resource_access', environment.auth.clientId, 'roles']))
    ].filter((role, index, roles) => roles.indexOf(role) === index);
  }

  private extractRoleArray(value: unknown): string[] {
    return Array.isArray(value) ? value.filter((role): role is string => typeof role === 'string') : [];
  }

  private readNestedClaim(payload: Record<string, unknown>, path: string[]): unknown {
    return path.reduce<unknown>((current, key) => {
      if (current && typeof current === 'object' && key in current) {
        return (current as Record<string, unknown>)[key];
      }
      return null;
    }, payload);
  }

  private readStringClaim(payload: Record<string, unknown>, claim: string): string | null {
    const value = payload[claim];
    return typeof value === 'string' ? value : null;
  }

  private normalizeRole(role: string): string {
    return role.trim().toUpperCase().replace(/^ROLE[-_:\s]?/, '').replace(/[\s-]+/g, '_');
  }

  private handleOAuthEvent(event: OAuthEvent): void {
    this.syncAuthState();

    if (event.type === 'token_expires') {
      void this.refreshAccessToken().then((refreshed) => {
        if (!refreshed) {
          this.handleUnauthorized();
        }
      });
    }

    if (event.type === 'session_terminated' || event.type === 'session_error') {
      this.clearSession();
    }
  }

  private storeReturnUrl(returnUrl: string): void {
    sessionStorage.setItem(RETURN_URL_STORAGE_KEY, this.getSafeReturnUrl(returnUrl));
  }

  private consumeReturnUrl(): string | null {
    const returnUrl = sessionStorage.getItem(RETURN_URL_STORAGE_KEY);
    sessionStorage.removeItem(RETURN_URL_STORAGE_KEY);
    return returnUrl ? this.getSafeReturnUrl(returnUrl) : null;
  }

  private restoreSavedReturnUrl(): void {
    const returnUrl = this.consumeReturnUrl();
    if (returnUrl && returnUrl !== this.currentBrowserPath()) {
      void this.router.navigateByUrl(returnUrl);
    }
  }

  private currentBrowserPath(): string {
    return `${window.location.pathname}${window.location.search}${window.location.hash}`;
  }
}
