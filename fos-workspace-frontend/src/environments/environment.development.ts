export const environment = {
  production: false,
  gatewayBaseUrl: 'http://localhost:8080',
  auth: {
    enabled: true,
    keycloakUrl: 'http://localhost:8180',
    realm: 'fos',
    clientId: 'fos-workspace-frontend'
  }
};
