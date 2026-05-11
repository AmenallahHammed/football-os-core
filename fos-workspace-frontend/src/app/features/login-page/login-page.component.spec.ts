import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { LoginPageComponent } from './login-page.component';

@Component({
  standalone: true,
  template: ''
})
class EmptyRouteComponent {}

describe('LoginPageComponent', () => {
  const originalAuthEnabled = environment.auth.enabled;
  let authService: jasmine.SpyObj<Pick<AuthService, 'isAuthenticated' | 'login' | 'getSafeReturnUrl'>>;
  let fixture: ComponentFixture<LoginPageComponent>;
  let router: Router;

  beforeEach(async () => {
    environment.auth.enabled = true;
    authService = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'login', 'getSafeReturnUrl']);
    authService.isAuthenticated.and.returnValue(false);
    authService.getSafeReturnUrl.and.callFake((returnUrl?: string | null) => returnUrl ?? '/workspace/calendar');

    await TestBed.configureTestingModule({
      imports: [LoginPageComponent],
      providers: [
        provideRouter([{ path: 'workspace/calendar', component: EmptyRouteComponent }]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap({ returnUrl: '/documents' })
            }
          }
        },
        { provide: AuthService, useValue: authService }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
  });

  afterEach(() => {
    environment.auth.enabled = originalAuthEnabled;
    TestBed.resetTestingModule();
  });

  it('starts auth flow with the requested return URL', () => {
    fixture = TestBed.createComponent(LoginPageComponent);
    fixture.detectChanges();

    clickSignIn();
    fixture.detectChanges();

    expect(authService.login).toHaveBeenCalledWith('/documents');
    expect(signInButton().disabled).toBeTrue();
    expect(signInButton().textContent?.trim()).toBe('Opening secure sign-in...');
  });

  it('shows an inline error if sign-in cannot be opened', () => {
    authService.login.and.throwError('Keycloak unavailable');
    fixture = TestBed.createComponent(LoginPageComponent);
    fixture.detectChanges();

    clickSignIn();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Secure sign-in could not be opened');
  });

  it('redirects authenticated users away from the sign-in page', () => {
    authService.isAuthenticated.and.returnValue(true);
    fixture = TestBed.createComponent(LoginPageComponent);

    fixture.detectChanges();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/documents');
  });

  function clickSignIn(): void {
    signInButton().click();
  }

  function signInButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('.signin-form__submit') as HTMLButtonElement;
  }
});
