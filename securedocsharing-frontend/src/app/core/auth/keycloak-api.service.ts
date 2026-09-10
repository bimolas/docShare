import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable, switchMap, throwError, catchError } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface KeycloakTokenResponse {
  access_token: string;
  refresh_token: string;
  id_token: string;
  expires_in: number;
  refresh_expires_in: number;
  token_type: string;
  session_state: string;
  scope: string;
}

export interface SignInResult {
  accessToken: string;
  refreshToken: string;
  idToken: string;
  expiresIn: number;
}

export interface SignUpRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

/**
 * KeycloakApiService
 *
 * Calls Keycloak REST endpoints directly from the Angular client.
 * No backend proxy is involved — credentials are sent straight to Keycloak.
 *
 * ── Sign-in ──
 * Uses the OIDC Resource Owner Password Credentials (ROPC) grant.
 * Requires "Direct access grants" enabled on the Keycloak client.
 *
 * ── Sign-up ──
 * Uses a two-step flow:
 *   1. Obtain a short-lived admin token via the registration-service client
 *      (a confidential Keycloak client with the realm-management:manage-users role).
 *   2. Call the Keycloak Admin REST API to create the user and trigger the
 *      verification email immediately using the returned user ID.
 *
 * ── Password reset ──
 * Triggers Keycloak's built-in forgot-password email using the Admin API.
 *
 * CORS: Keycloak's token endpoint and admin API support CORS by default when
 * the client's "Web Origins" are configured. Ensure `http://localhost:4200`
 * (or your app origin) is listed in the Keycloak client's Web Origins.
 */
@Injectable({ providedIn: 'root' })
export class KeycloakApiService {
  private readonly http = inject(HttpClient);

  private readonly kcBase = environment.keycloak.url;
  private readonly realm = environment.keycloak.realm;
  private readonly clientId = environment.keycloak.clientId;

  // ── Token endpoint ──────────────────────────────────────────────────────────
  private get tokenUrl(): string {
    return `${this.kcBase}/realms/${this.realm}/protocol/openid-connect/token`;
  }

  // ── Admin API base ──────────────────────────────────────────────────────────
  private get adminUsersUrl(): string {
    return `${this.kcBase}/admin/realms/${this.realm}/users`;
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SIGN IN — ROPC grant (email + password → tokens)
  // ══════════════════════════════════════════════════════════════════════════

  /**
   * Authenticates the user via the Keycloak ROPC token grant.
   * Returns the raw token response including access_token and refresh_token.
   *
   * Prerequisites:
   *   • Keycloak client "docshare-app" must have "Direct access grants" = ON
   */
  signIn(email: string, password: string): Observable<KeycloakTokenResponse> {
    const body = new HttpParams()
      .set('grant_type', 'password')
      .set('client_id', this.clientId)
      .set('username', email.trim().toLowerCase())
      .set('password', password)
      .set('scope', 'openid profile email');

    return this.http.post<KeycloakTokenResponse>(this.tokenUrl, body.toString(), {
      headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }),
    }).pipe(
      catchError((err) => {
        const code = err?.error?.error;
        const desc = err?.error?.error_description || '';
        if (code === 'invalid_grant') {
          if (
            desc.toLowerCase().includes('not fully set up') ||
            desc.toLowerCase().includes('email') ||
            desc.toLowerCase().includes('required action')
          ) {
            return throwError(() => new Error('EMAIL_NOT_VERIFIED: Your email address has not been verified yet. Please check your inbox to activate your account.'));
          }
          return throwError(() => new Error('Invalid email or password. Please try again.'));
        }
        if (code === 'account_disabled') {
          return throwError(() => new Error('Your account has been disabled. Please contact support.'));
        }
        return throwError(() => new Error('Unable to sign in. Please check your connection and try again.'));
      })
    );
  }

  // ══════════════════════════════════════════════════════════════════════════
  // SIGN UP — Admin API via service-account client credentials
  // ══════════════════════════════════════════════════════════════════════════

  /**
   * Step 1 — Obtain a short-lived admin token using the registration service account.
   */
  private getRegistrationAdminToken(): Observable<string> {
    const body = new HttpParams()
      .set('grant_type', 'client_credentials')
      .set('client_id', environment.keycloak.registrationClientId)
      .set('client_secret', environment.keycloak.registrationClientSecret);

    return this.http.post<KeycloakTokenResponse>(this.tokenUrl, body.toString(), {
      headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }),
    }).pipe(
      map(res => res.access_token),
      catchError(() => throwError(() => new Error('Registration service unavailable. Please try again later.')))
    );
  }

  /**
   * Step 2 — Create the user in Keycloak using the admin token.
   * Extracts and returns the created User ID UUID from the response Location header.
   */
  private createKeycloakUser(adminToken: string, req: SignUpRequest): Observable<string> {
    const payload = {
      username: req.email.trim().toLowerCase(),
      email: req.email.trim().toLowerCase(),
      firstName: req.firstName.trim(),
      lastName: req.lastName.trim(),
      enabled: true,
      emailVerified: false,
      credentials: [
        {
          type: 'password',
          value: req.password,
          temporary: false,
        }
      ],
      requiredActions: ['VERIFY_EMAIL'],
    };

    return this.http.post(this.adminUsersUrl, payload, {
      headers: new HttpHeaders({
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${adminToken}`,
      }),
      observe: 'response',
    }).pipe(
      map(response => {
        const location = response.headers.get('Location');
        if (location) {
          const parts = location.split('/');
          return parts[parts.length - 1];
        }
        return '';
      }),
      catchError((err) => {
        if (err?.status === 409) {
          return throwError(() => new Error(
            'An account with this email already exists. Please sign in instead.'
          ));
        }
        return throwError(() => new Error('Registration failed. Please try again.'));
      })
    );
  }

  /**
   * Registers a new user and triggers the verification email.
   * Obtains an admin token, creates the user, and triggers execute-actions-email immediately.
   */
  signUp(req: SignUpRequest): Observable<void> {
    return this.getRegistrationAdminToken().pipe(
      switchMap(adminToken =>
        this.createKeycloakUser(adminToken, req).pipe(
          switchMap(userId => this.sendVerificationEmail(req.email, userId, adminToken))
        )
      )
    );
  }

  // ══════════════════════════════════════════════════════════════════════════
  // FORGOT PASSWORD — trigger reset email via Admin API
  // ══════════════════════════════════════════════════════════════════════════

  /**
   * Sends a password reset email to the given address using the admin API.
   */
  sendPasswordResetEmail(email: string): Observable<void> {
    return this.getRegistrationAdminToken().pipe(
      switchMap(adminToken => {
        const headers = new HttpHeaders({ 'Authorization': `Bearer ${adminToken}` });

        // Find user by email
        return this.http.get<{ id: string }[]>(
          `${this.adminUsersUrl}?email=${encodeURIComponent(email.trim().toLowerCase())}&exact=true`,
          { headers }
        ).pipe(
          switchMap(users => {
            if (!users || users.length === 0) {
              // Return success anyway — don't expose whether the email exists
              return new Observable<void>(obs => { obs.next(); obs.complete(); });
            }
            const userId = users[0].id;
            const redirectUri = encodeURIComponent(`${window.location.origin}/sign-in`);
            return this.http.put<void>(
              `${this.adminUsersUrl}/${userId}/execute-actions-email?client_id=${this.clientId}&redirect_uri=${redirectUri}`,
              ['UPDATE_PASSWORD'],
              { headers: new HttpHeaders({ 'Authorization': `Bearer ${adminToken}`, 'Content-Type': 'application/json' }) }
            );
          })
        );
      }),
      catchError((err) => {
        console.error('[KeycloakApiService] Password reset request error:', err);
        return new Observable<void>(obs => { obs.next(); obs.complete(); });
      })
    );
  }

  /**
   * Sends a verification email to the given address using the admin API.
   * If userId and token are passed, triggers immediately without querying GET /users.
   */
  sendVerificationEmail(email: string, userId?: string, adminTokenInput?: string): Observable<void> {
    const executeEmail = (token: string, uId: string) => {
      const redirectUri = encodeURIComponent(`${window.location.origin}/sign-in`);
      return this.http.put<void>(
        `${this.adminUsersUrl}/${uId}/execute-actions-email?client_id=${this.clientId}&redirect_uri=${redirectUri}`,
        ['VERIFY_EMAIL'],
        {
          headers: new HttpHeaders({
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
          })
        }
      );
    };

    if (userId && adminTokenInput) {
      return executeEmail(adminTokenInput, userId).pipe(
        catchError((err) => {
          console.error('[KeycloakApiService] Failed to execute-actions-email:', err);
          return throwError(() => new Error('Failed to send verification email. Please try again.'));
        })
      );
    }

    return this.getRegistrationAdminToken().pipe(
      switchMap(adminToken => {
        const headers = new HttpHeaders({ 'Authorization': `Bearer ${adminToken}` });
        if (userId) {
          return executeEmail(adminToken, userId);
        }
        return this.http.get<{ id: string }[]>(
          `${this.adminUsersUrl}?email=${encodeURIComponent(email.trim().toLowerCase())}&exact=true`,
          { headers }
        ).pipe(
          switchMap(users => {
            if (!users || users.length === 0) {
              return throwError(() => new Error('User not found.'));
            }
            return executeEmail(adminToken, users[0].id);
          })
        );
      }),
      catchError((err) => {
        console.error('[KeycloakApiService] Failed to send verification email:', err);
        return throwError(() => new Error(err?.message || 'Failed to send verification email. Please try again.'));
      })
    );
  }

  // ══════════════════════════════════════════════════════════════════════════
  // TOKEN REFRESH — direct to Keycloak token endpoint
  // ══════════════════════════════════════════════════════════════════════════

  /**
   * Exchanges a refresh token for a new access token directly with Keycloak.
   * Used by the ROPC sign-in flow (not the OIDC redirect flow).
   */
  refreshToken(refreshToken: string): Observable<KeycloakTokenResponse> {
    const body = new HttpParams()
      .set('grant_type', 'refresh_token')
      .set('client_id', this.clientId)
      .set('refresh_token', refreshToken);

    return this.http.post<KeycloakTokenResponse>(this.tokenUrl, body.toString(), {
      headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }),
    }).pipe(
      catchError(() => throwError(() => new Error('Session expired. Please sign in again.')))
    );
  }
}
