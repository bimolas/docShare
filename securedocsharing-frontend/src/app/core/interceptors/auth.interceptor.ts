import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { KeycloakService } from '../auth/keycloak.service';

/**
 * authInterceptor: Functional interceptor that attaches JWT Bearer token
 * from KeycloakService to every outgoing API request.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const keycloakService = inject(KeycloakService);
  const token = keycloakService.getToken();

  const isInternalApi = req.url.startsWith('/api') || req.url.includes('/api/');
  const isS3PresignedUrl = req.url.includes('X-Amz-Algorithm') || req.url.includes('X-Amz-Credential');

  if (!token || !isInternalApi || isS3PresignedUrl) {
    return next(req);
  }

  const authenticatedReq = req.clone({
    setHeaders: { Authorization: `Bearer ${token}` },
  });

  return next(authenticatedReq);
};
