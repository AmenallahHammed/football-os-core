import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { AuthGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('AuthGuard', () => {
  const originalAuthEnabled = environment.auth.enabled;
  let authService: jasmine.SpyObj<Pick<AuthService, 'isAuthenticated' | 'getSafeReturnUrl'>>;
  let guard: AuthGuard;
  let router: Router;

  beforeEach(() => {
    environment.auth.enabled = true;
    authService = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'getSafeReturnUrl']);
    authService.getSafeReturnUrl.and.callFake((returnUrl?: string | null) => returnUrl ?? '/workspace/calendar');

    TestBed.configureTestingModule({
      providers: [provideRouter([]), AuthGuard, { provide: AuthService, useValue: authService }]
    });

    guard = TestBed.inject(AuthGuard);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    environment.auth.enabled = originalAuthEnabled;
    TestBed.resetTestingModule();
  });

  it('allows authenticated users through', () => {
    authService.isAuthenticated.and.returnValue(true);

    expect(guard.canActivate({} as ActivatedRouteSnapshot, state('/documents'))).toBeTrue();
  });

  it('redirects unauthenticated users to login with returnUrl', () => {
    authService.isAuthenticated.and.returnValue(false);

    const result = guard.canActivate({} as ActivatedRouteSnapshot, state('/documents'));

    expect(router.serializeUrl(result as ReturnType<Router['createUrlTree']>)).toBe('/login?returnUrl=%2Fdocuments');
  });

  it('allows all routes when auth is disabled', () => {
    environment.auth.enabled = false;
    authService.isAuthenticated.and.returnValue(false);

    expect(guard.canActivate({} as ActivatedRouteSnapshot, state('/documents'))).toBeTrue();
  });
});

function state(url: string): RouterStateSnapshot {
  return { url } as RouterStateSnapshot;
}
