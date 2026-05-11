import { AuthConfig } from 'angular-oauth2-oidc';

import { environment } from '../../../environments/environment';

export function buildKeycloakAuthConfig(): AuthConfig {
  const keycloakUrl = environment.auth.keycloakUrl.replace(/\/+$/, '');

  return {
    issuer: `${keycloakUrl}/realms/${environment.auth.realm}`,
    redirectUri: window.location.origin,
    postLogoutRedirectUri: `${window.location.origin}/login`,
    clientId: environment.auth.clientId,
    responseType: 'code',
    scope: 'openid profile email',
    requireHttps: false,
    strictDiscoveryDocumentValidation: false,
    showDebugInformation: !environment.production,
    useSilentRefresh: true,
    disablePKCE: false,
    preserveRequestedRoute: true
  };
}
