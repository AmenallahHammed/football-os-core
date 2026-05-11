import { HTTP_INTERCEPTORS, HttpClient } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AuthInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('AuthInterceptor', () => {
  const originalAuthEnabled = environment.auth.enabled;
  const originalGatewayBaseUrl = environment.gatewayBaseUrl;
  let authService: jasmine.SpyObj<Pick<AuthService, 'getAccessToken' | 'handleUnauthorized'>>;
  let http: HttpClient;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    environment.auth.enabled = true;
    environment.gatewayBaseUrl = 'http://localhost:8080';
    authService = jasmine.createSpyObj('AuthService', ['getAccessToken', 'handleUnauthorized']);
    authService.getAccessToken.and.returnValue('access-token');

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }
      ]
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    environment.auth.enabled = originalAuthEnabled;
    environment.gatewayBaseUrl = originalGatewayBaseUrl;
    TestBed.resetTestingModule();
  });

  it('attaches bearer token to gateway API requests', () => {
    http.get(`${environment.gatewayBaseUrl}/api/v1/events`).subscribe();

    const request = httpTesting.expectOne(`${environment.gatewayBaseUrl}/api/v1/events`);
    expect(request.request.headers.get('Authorization')).toBe('Bearer access-token');
    request.flush({});
  });

  it('does not attach bearer token to static assets or unrelated hosts', () => {
    http.get('assets/ball.png').subscribe();
    http.get('https://cdn.example.com/file.json').subscribe();

    const assetRequest = httpTesting.expectOne('assets/ball.png');
    const cdnRequest = httpTesting.expectOne('https://cdn.example.com/file.json');
    expect(assetRequest.request.headers.has('Authorization')).toBeFalse();
    expect(cdnRequest.request.headers.has('Authorization')).toBeFalse();
    assetRequest.flush('');
    cdnRequest.flush({});
  });

  it('clears local auth state on gateway 401 responses', () => {
    http.get(`${environment.gatewayBaseUrl}/api/v1/events`).subscribe({ error: () => undefined });

    const request = httpTesting.expectOne(`${environment.gatewayBaseUrl}/api/v1/events`);
    request.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(authService.handleUnauthorized).toHaveBeenCalled();
  });

  it('does not logout on gateway 403 responses', () => {
    http.get(`${environment.gatewayBaseUrl}/api/v1/events`).subscribe({ error: () => undefined });

    const request = httpTesting.expectOne(`${environment.gatewayBaseUrl}/api/v1/events`);
    request.flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

    expect(authService.handleUnauthorized).not.toHaveBeenCalled();
  });
});
