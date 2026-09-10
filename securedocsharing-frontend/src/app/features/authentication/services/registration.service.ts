import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';

export interface RegistrationRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

export interface OnboardingData {
  role: string;
  hearAboutUs: string;
  useCase: string;
  teamSize?: string;
}

export interface RegistrationResponse {
  success: boolean;
  userId?: string;
  message?: string;
}

/**
 * RegistrationService: Handles new user registration flow.
 * Communicates with the backend which proxies to Keycloak Admin REST API.
 * Self-registration does NOT require admin tokens on the frontend.
 */
@Injectable({ providedIn: 'root' })
export class RegistrationService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = environment.apiBaseUrl;

  /**
   * Register a new user. The backend calls Keycloak Admin REST API
   * and triggers a verification email automatically.
   */
  register(data: RegistrationRequest): Observable<RegistrationResponse> {
    return this.http
      .post<RegistrationResponse>(`${this.apiBase}/v1/auth/register`, data)
      .pipe(
        catchError((err) => {
          // HTTP 409 = email already registered (Keycloak "User exists with same email")
          if (err?.status === 409) {
            return throwError(() => new Error(
              'An account with this email already exists. Please sign in instead, or use "Forgot password" if needed.'
            ));
          }
          const message =
            err?.error?.message ||
            err?.error?.errorMessage ||
            'Registration failed. Please try again.';
          return throwError(() => new Error(message));
        })
      );
  }

  /**
   * Save onboarding data after the user verifies their email and signs in.
   * This updates the user profile with context gathered during onboarding.
   */
  saveOnboarding(data: OnboardingData): Observable<void> {
    return this.http
      .post<void>(`${this.apiBase}/v1/users/me/onboarding`, data)
      .pipe(
        catchError((err) =>
          throwError(() => new Error(err?.error?.message || 'Failed to save preferences.'))
        )
      );
  }

  /**
   * Resend verification email for the given email address.
   */
  resendVerificationEmail(email: string): Observable<void> {
    return this.http
      .post<void>(`${this.apiBase}/v1/auth/resend-verification`, { email })
      .pipe(
        catchError((err) =>
          throwError(() => new Error(err?.error?.message || 'Failed to resend email.'))
        )
      );
  }
}
