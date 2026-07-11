export const environment = {
  production: false,
  // Requests go through the Angular dev-server proxy (see proxy.conf.json),
  // so relative paths reach the Spring Boot backend on :8080.
  apiUrl: '/api',
  wsUrl: '/ws',
};
