import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, convertToParamMap, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { PublicAuthGuard } from './public-auth.guard';

describe('PublicAuthGuard', () => {
  const originalAuthEnabled = environment.auth.enabled;
  let authService: jasmine.SpyObj<Pick<AuthService, 'isAuthenticated' | 'getSafeReturnUrl'>>;
  let guard: PublicAuthGuard;
  let router: Router;

  beforeEach(() => {
    environment.auth.enabled = true;
    authService = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'getSafeReturnUrl']);
    authService.getSafeReturnUrl.and.callFake((returnUrl?: string | null) => returnUrl ?? '/workspace/calendar');

    TestBed.configureTestingModule({
      providers: [provideRouter([]), PublicAuthGuard, { provide: AuthService, useValue: authService }]
    });

    guard = TestBed.inject(PublicAuthGuard);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    environment.auth.enabled = originalAuthEnabled;
    TestBed.resetTestingModule();
  });

  it('allows unauthenticated users to open public auth routes', () => {
    authService.isAuthenticated.and.returnValue(false);

    expect(guard.canActivate(route('/documents'))).toBeTrue();
  });

  it('redirects authenticated users away from login', () => {
    authService.isAuthenticated.and.returnValue(true);

    const result = guard.canActivate(route('/documents'));

    expect(router.serializeUrl(result as ReturnType<Router['parseUrl']>)).toBe('/documents');
  });

  it('allows public routes when auth is disabled', () => {
    environment.auth.enabled = false;
    authService.isAuthenticated.and.returnValue(true);

    expect(guard.canActivate(route('/documents'))).toBeTrue();
  });
});

function route(returnUrl: string): ActivatedRouteSnapshot {
  return { queryParamMap: convertToParamMap({ returnUrl }) } as ActivatedRouteSnapshot;
}
