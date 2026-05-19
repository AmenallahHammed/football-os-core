export const environment = {
  production: true,
  gatewayBaseUrl: 'http://localhost:8080',
  onlyOfficeDocumentServerUrl: '',
  devFallbackTeamId: '',
  devFallbackClubId: '',
  devFallbackActorId: '',
  auth: {
    enabled: true,
    keycloakUrl: 'http://localhost:8180',
    realm: 'fos',
    clientId: 'fos-workspace-frontend'
  }
};
