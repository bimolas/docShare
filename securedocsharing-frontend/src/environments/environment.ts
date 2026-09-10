export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8081/api',
  keycloak: {
    url: 'http://localhost:8080',
    realm: 'docshare',
    clientId: 'docshare-app',
    // Service-account client used for user creation (sign-up) and password-reset
    // email triggering via the Keycloak Admin REST API.
    // Create in Keycloak Admin → Clients → "docshare-registration"
    //   Service Accounts Enabled = ON
    //   Roles → assign realm-management → manage-users
    registrationClientId: 'docshare-registration',
    registrationClientSecret: 'registration-secret-123456',
  },
};
