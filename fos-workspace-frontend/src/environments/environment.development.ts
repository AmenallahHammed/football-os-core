export const environment = {
  production: false,
  gatewayBaseUrl: 'http://localhost:8080',
  onlyOfficeDocumentServerUrl: 'http://localhost:8090',
  auth: {
    enabled: true,
    keycloakUrl: 'http://localhost:8180',
    realm: 'fos',
    clientId: 'fos-workspace-frontend'
  }
};
