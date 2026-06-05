export const environment = {
  production: false,
  gatewayBaseUrl: 'http://localhost:8080',
  onlyOfficeDocumentServerUrl: '',
  // Temporary local placeholder IDs until auth-backed actor/team lookup is wired.
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
