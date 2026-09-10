import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, from, of, throwError } from 'rxjs';
import { tap, catchError, switchMap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { UserProfile } from '../models/user.model';

/**
 * AuthService: Owns authentication state and Keycloak session management.
 * Business logic: login/logout/token refresh.
 * State: currentUser signal, isAuthenticated computed.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  // --- State Signals ---
  private readonly _currentUser = signal<UserProfile | null>(null);
  private readonly _isLoading = signal<boolean>(false);
  private readonly _accessToken = signal<string | null>(null);

  // --- Computed ---
  readonly currentUser = this._currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this._currentUser() !== null);
  readonly isLoading = this._isLoading.asReadonly();
  readonly accessToken = this._accessToken.asReadonly();

  /**
   * Load profile from API after Keycloak token is available.
   * Call this once after Keycloak init succeeds.
   */
  loadUserProfile(): Observable<UserProfile> {
    this._isLoading.set(true);
    return this.http.get<UserProfile>(`${environment.apiBaseUrl}/v1/users/me`).pipe(
      tap((profile) => {
        this._currentUser.set(profile);
        this._isLoading.set(false);
      }),
      catchError((err) => {
        this._isLoading.set(false);
        return throwError(() => err);
      })
    );
  }

  /**
   * Store the token retrieved from Keycloak — called by the auth interceptor bootstrap.
   */
  setAccessToken(token: string): void {
    this._accessToken.set(token);
  }

  logout(): void {
    this._currentUser.set(null);
    this._accessToken.set(null);
    this.router.navigate(['/login']);
  }
}
