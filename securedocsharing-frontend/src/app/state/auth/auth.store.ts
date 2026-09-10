import { Injectable, signal, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { KeycloakService } from '../../core/auth/keycloak.service';
import { UserApiService } from '../../core/services/user-api.service';
import { UserProfile, mapUserResponseToProfile } from '../../core/models/user.model';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError, Observable } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly keycloakService = inject(KeycloakService);
  private readonly userApiService = inject(UserApiService);
  private readonly router = inject(Router);

  // State Signals
  private readonly _currentUser = signal<UserProfile | null>(null);
  private readonly _isLoading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);

  // Readonly Signals & Computed State
  readonly currentUser = this._currentUser.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();
  readonly error = this._error.asReadonly();

  readonly isAuthenticated = computed(() => {
    return this.keycloakService.isLoggedIn() && this._currentUser() !== null;
  });

  readonly userInitials = computed(() => {
    const user = this._currentUser();
    if (!user) return '??';
    const first = user.firstName ? user.firstName.charAt(0) : '';
    const last = user.lastName ? user.lastName.charAt(0) : '';
    return (first + last).toUpperCase() || user.email.charAt(0).toUpperCase();
  });

  /**
   * Syncs user profile from Spring Boot backend (/api/v1/users/me)
   * after Keycloak OIDC authentication is verified.
   */
  loadUserProfile(): Observable<UserProfile | null> {
    console.log('[AUTH] AuthStore.loadUserProfile() START', Date.now(), typeof window !== 'undefined' ? window.location.href : '');

    // Require both the authenticated flag AND a valid token before hitting the API.
    // If only the flag is set (phantom auth), skip the call to avoid a 401.
    if (!this.keycloakService.isLoggedIn() || !this.keycloakService.getToken()) {
      console.log('[AUTH] AuthStore.loadUserProfile() SKIPPED: not logged in or no token', Date.now());
      this._currentUser.set(null);
      return of(null);
    }

    this._isLoading.set(true);
    this._error.set(null);

    return this.userApiService.getMyProfile().pipe(
      map((dto) => {
        const profile = mapUserResponseToProfile(dto);
        this._currentUser.set(profile);
        this._isLoading.set(false);
        return profile;
      }),
      catchError((err) => {
        this._isLoading.set(false);

        // 401: token was rejected → clear auth state and redirect to sign-in.
        // The error interceptor already tried a refresh; if we're here it failed.
        if (err instanceof HttpErrorResponse && err.status === 401) {
          console.warn('[AUTH] AuthStore.loadUserProfile() 401 — clearing state and redirecting to sign-in.');
          this._currentUser.set(null);
          this.keycloakService.clearTokensAndState();
          this.router.navigate(['/sign-in'], { replaceUrl: true });
          return of(null);
        }

        // Check if it's a normalized AppError (from error interceptor) with status 401
        if (err && err.status === 401) {
          console.warn('[AUTH] AuthStore.loadUserProfile() AppError 401 — clearing state and redirecting to sign-in.');
          this._currentUser.set(null);
          this.keycloakService.clearTokensAndState();
          this.router.navigate(['/sign-in'], { replaceUrl: true });
          return of(null);
        }

        console.error('Failed to load user profile in AuthStore:', err);
        this._error.set('Failed to load profile details.');
        return throwError(() => err);
      })
    );
  }

  /**
   * Triggers OIDC login via KeycloakService.
   */
  login(): void {
    this.keycloakService.login();
  }

  /**
   * Triggers OIDC logout via KeycloakService and resets store state.
   */
  logout(): void {
    this._currentUser.set(null);
    this._error.set(null);
    this.keycloakService.logout();
  }

  /**
   * Directly sets user profile if pre-fetched.
   */
  setUserProfile(profile: UserProfile | null): void {
    this._currentUser.set(profile);
  }
}
