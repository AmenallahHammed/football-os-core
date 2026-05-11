import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(private readonly authService: AuthService) {}

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const shouldAttachToken = environment.auth.enabled && this.isGatewayApiRequest(req) && !req.headers.has('Authorization');
    const accessToken = this.authService.getAccessToken();
    const authenticatedReq =
      shouldAttachToken && accessToken
        ? req.clone({
            setHeaders: {
              Authorization: `Bearer ${accessToken}`
            }
          })
        : req;

    return next.handle(authenticatedReq).pipe(
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && this.isGatewayApiRequest(req) && error.status === 401) {
          this.authService.handleUnauthorized();
        }

        return throwError(() => error);
      })
    );
  }

  private isGatewayApiRequest(req: HttpRequest<unknown>): boolean {
    try {
      const gatewayUrl = new URL(environment.gatewayBaseUrl, window.location.origin);
      const requestUrl = new URL(req.url, window.location.origin);
      const gatewayBasePath = gatewayUrl.pathname.replace(/\/+$/, '');
      const apiPrefix = gatewayBasePath ? `${gatewayBasePath}/api/v1/` : '/api/v1/';

      return requestUrl.origin === gatewayUrl.origin && requestUrl.pathname.startsWith(apiPrefix);
    } catch {
      return false;
    }
  }
}
