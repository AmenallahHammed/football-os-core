export const environment = {
  production: false,
  gatewayBaseUrl: 'http://localhost:8080',
  onlyOfficeDocumentServerUrl: '',
  // Local no-auth development fallbacks only. Replace with real local IDs for
  // authenticated end-to-end checks.
  devFallbackTeamId: '00000000-0000-0000-0000-000000000001',
  devFallbackClubId: '00000000-0000-0000-0000-000000000001',
  devFallbackActorId: '11111111-1111-1111-1111-111111111101',
  auth: {
    enabled: true,
    keycloakUrl: 'http://localhost:8180',
    realm: 'fos',
    clientId: 'fos-workspace-frontend'
  }
};
