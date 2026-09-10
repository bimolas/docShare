import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { KeycloakService } from '../auth/keycloak.service';
import { NotificationService } from '../services/notification.service';
import { normalizeHttpError } from '../models/app-error.model';

/**
 * errorInterceptor: Functional interceptor for global HTTP error handling.
 * - 401 Unauthorized → Triggers KeycloakService concurrency-locked silent token refresh and retries failed request.
 * - Enforces 401 Single-Retry Invariant: Prevents infinite 401 retry loops by checking 'X-Retry-Attempt'.
 * - 403, 404, 409, 5xx, Network errors → Normalizes to AppError and triggers toast notifications.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const keycloakService = inject(KeycloakService);
  const notificationService = inject(NotificationService);

  // Pass Keycloak / external requests through untouched.
  // They have dedicated error handling in KeycloakApiService and must NOT be
  // transformed into AppError — that would strip the raw Keycloak error body
  // (error: 'invalid_grant', error_description: '…') before the service's
  // own catchError can inspect it. External requests also send simple CORS
  // headers only; adding custom headers (X-Silent-Error) would trigger a
  // CORS preflight that Keycloak doesn't support for that header.
  const isExternalRequest = req.url.startsWith('http') &&
    !req.url.startsWith('http://localhost:8081') &&
    !req.url.startsWith('http://localhost:4200');
  if (isExternalRequest) {
    return next(req);
  }

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        console.log('[AUTH] errorInterceptor 401 Unauthorized caught', Date.now(), typeof window !== 'undefined' ? window.location.href : '', 'req url:', req.url);
        // Enforce 401 Single-Retry Invariant:
        // If request was already retried once (has X-Retry-Attempt header), DO NOT retry again.
        if (req.headers.has('X-Retry-Attempt')) {
          console.warn('401 Unauthorized encountered on retried request. Clearing auth state.');
          keycloakService.clearTokensAndState();
          const appErr = normalizeHttpError(error, req.url);
          return throwError(() => appErr);
        }

        // First 401 attempt: trigger KeycloakService silent token refresh (forcing refresh check with -1)
        return keycloakService.refreshToken(-1).pipe(
          switchMap((refreshed) => {
            const newToken = keycloakService.getToken();
            if (refreshed && newToken) {
              const retriedReq = req.clone({
                setHeaders: {
                  Authorization: `Bearer ${newToken}`,
                  'X-Retry-Attempt': 'true',
                },
              });
              return next(retriedReq);
            } else {
              // Refresh failed or session completely expired -> Clear auth state without forcing infinite page redirect
              console.warn('Keycloak token refresh failed. Clearing auth state.');
              keycloakService.clearTokensAndState();
              const appErr = normalizeHttpError(error, req.url);
              return throwError(() => appErr);
            }
          }),
          catchError(() => {
            keycloakService.clearTokensAndState();
            const appErr = normalizeHttpError(error, req.url);
            return throwError(() => appErr);
          })
        );
      }

      const appError = normalizeHttpError(error, req.url);

      // Display notification toast for actionable user-facing errors (unless explicitly silenced)
      if (appError.status !== 401 && !req.headers.has('X-Silent-Error')) {
        notificationService.error(appError.userFriendlyMessage);
      }

      return throwError(() => appError);
    })
  );
};
