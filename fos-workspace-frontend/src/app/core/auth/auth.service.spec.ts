import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { OAuthService } from 'angular-oauth2-oidc';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

class MockOAuthService {
  readonly events = new Subject<{ type: string }>();
  readonly configure = jasmine.createSpy('configure');
  readonly setStorage = jasmine.createSpy('setStorage');
  readonly loadDiscoveryDocumentAndTryLogin = jasmine.createSpy('loadDiscoveryDocumentAndTryLogin').and.resolveTo(true);
  readonly setupAutomaticSilentRefresh = jasmine.createSpy('setupAutomaticSilentRefresh');
  readonly initCodeFlow = jasmine.createSpy('initCodeFlow');
  readonly logOut = jasmine.createSpy('logOut');
  readonly refreshToken = jasmine.createSpy('refreshToken').and.resolveTo({});
  readonly hasValidAccessToken = jasmine.createSpy('hasValidAccessToken').and.returnValue(false);
  readonly getAccessToken = jasmine.createSpy('getAccessToken').and.returnValue('');
  readonly getRefreshToken = jasmine.createSpy('getRefreshToken').and.returnValue('');
}

describe('AuthService', () => {
  const originalAuthEnabled = environment.auth.enabled;
  const originalClientId = environment.auth.clientId;
  let oauthService: MockOAuthService;

  afterEach(() => {
    environment.auth.enabled = originalAuthEnabled;
    environment.auth.clientId = originalClientId;
    sessionStorage.clear();
    TestBed.resetTestingModule();
  });

  function setup(authEnabled = true): AuthService {
    environment.auth.enabled = authEnabled;
    environment.auth.clientId = 'fos-workspace-frontend';
    oauthService = new MockOAuthService();

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: OAuthService, useValue: oauthService }]
    });

    return TestBed.inject(AuthService);
  }

  it('configures OIDC with session storage when auth is enabled', async () => {
    const service = setup();

    expect(oauthService.configure).toHaveBeenCalled();
    expect(oauthService.setStorage).toHaveBeenCalledWith(sessionStorage);

    await service.initializeAuth();

    expect(oauthService.loadDiscoveryDocumentAndTryLogin).toHaveBeenCalled();
    expect(oauthService.setupAutomaticSilentRefresh).toHaveBeenCalledWith({}, 'access_token', true);
  });

  it('starts code flow with a safe stored return URL', () => {
    const service = setup();

    service.login('/documents');

    expect(sessionStorage.getItem('fos.auth.returnUrl')).toBe('/documents');
    expect(oauthService.initCodeFlow).toHaveBeenCalledWith('/documents');
  });

  it('reads access token claims and normalizes roles', async () => {
    const service = setup();
    const token = jwtWithPayload({
      sub: '11111111-1111-1111-1111-111111111101',
      roles: ['ROLE_HEAD_COACH'],
      realm_access: { roles: ['realm-admin'] },
      resource_access: { 'fos-workspace-frontend': { roles: ['client-role'] } },
      fos_club_id: '00000000-0000-0000-0000-000000000001',
      email: 'coach@fos.club',
      name: 'Head Coach',
      preferred_username: 'coach'
    });

    oauthService.hasValidAccessToken.and.returnValue(true);
    oauthService.getAccessToken.and.returnValue(token);

    await service.initializeAuth();

    expect(service.getTokenClaims()).toEqual({
      sub: '11111111-1111-1111-1111-111111111101',
      roles: ['ROLE_HEAD_COACH', 'realm-admin', 'client-role'],
      fos_club_id: '00000000-0000-0000-0000-000000000001',
      email: 'coach@fos.club',
      name: 'Head Coach',
      preferred_username: 'coach'
    });
    expect(service.roles()).toEqual(['HEAD_COACH', 'REALM_ADMIN', 'CLIENT_ROLE']);
    expect(service.hasRole('ROLE_HEAD_COACH')).toBeTrue();
    expect(service.currentActorId()).toBe('11111111-1111-1111-1111-111111111101');
    expect(service.currentClubId()).toBe('00000000-0000-0000-0000-000000000001');
  });

  it('treats disabled auth as locally authenticated without tokens', () => {
    const service = setup(false);

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.getAccessToken()).toBeNull();
    expect(oauthService.configure).not.toHaveBeenCalled();
  });
});

function jwtWithPayload(payload: Record<string, unknown>): string {
  return `${base64Url({ alg: 'none' })}.${base64Url(payload)}.signature`;
}

function base64Url(value: Record<string, unknown>): string {
  return btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}
