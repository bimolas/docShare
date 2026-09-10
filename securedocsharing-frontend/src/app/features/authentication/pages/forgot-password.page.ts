import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { KeycloakApiService } from '../../../core/auth/keycloak-api.service';

type ForgotState = 'input' | 'sent';

/**
 * ForgotPasswordPageComponent: 21st.dev-inspired high-craft password reset page.
 * Allows users to request password reset emails and provides direct links to open inbox.
 */
@Component({
  selector: 'app-forgot-password-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="auth-root">
      <!-- Background decoration -->
      <div class="auth-bg" aria-hidden="true">
        <div class="auth-bg-orb auth-bg-orb--1"></div>
        <div class="auth-bg-orb auth-bg-orb--2"></div>
        <div class="auth-bg-grid"></div>
      </div>

      <!-- Main card container -->
      <main class="auth-main">
        <div class="auth-card" role="main">

          <!-- Logo -->
          <div class="auth-brand-badge">
            <svg width="28" height="28" viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="36" height="36" rx="10" fill="#fb923c"/>
              <path d="M10 14a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v2a4 4 0 0 1-4 4h-8a4 4 0 0 1-4-4v-2Z" fill="white" opacity="0.9"/>
              <rect x="10" y="22" width="16" height="4" rx="2" fill="white" opacity="0.5"/>
            </svg>
            <span class="auth-brand-title">SecureDocSharing</span>
          </div>

          @if (state() === 'input') {
            <header class="auth-card-header">
              <h1 class="auth-card-title">Reset your password</h1>
              <p class="auth-card-subtitle">
                Enter your registered email address and we'll send you instructions to reset your password.
              </p>
            </header>

            <form class="auth-form" (ngSubmit)="sendResetEmail()" novalidate>
              <div class="auth-field">
                <label class="auth-label" for="reset-email">Email address</label>
                <div class="auth-input-wrap">
                  <svg class="auth-input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <rect width="20" height="16" x="2" y="4" rx="2"/>
                    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
                  </svg>
                  <input
                    id="reset-email"
                    name="email"
                    type="email"
                    class="auth-input"
                    [(ngModel)]="email"
                    placeholder="you@company.com"
                    autocomplete="email"
                    autofocus
                    required
                    [disabled]="isLoading()" />
                </div>
              </div>

              @if (errorMessage()) {
                <div class="auth-error" role="alert">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <circle cx="12" cy="12" r="10"/>
                    <line x1="12" y1="8" x2="12" y2="12"/>
                    <line x1="12" y1="16" x2="12.01" y2="16"/>
                  </svg>
                  <span>{{ errorMessage() }}</span>
                </div>
              }

              <button
                id="btn-send-reset"
                class="btn-primary"
                type="submit"
                [disabled]="isLoading() || !isValidEmail">
                @if (isLoading()) {
                  <span class="btn-spinner" aria-hidden="true"></span>
                  <span>Sending reset link…</span>
                } @else {
                  <span>Send reset link</span>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <line x1="5" y1="12" x2="19" y2="12"/>
                    <polyline points="12 5 19 12 12 19"/>
                  </svg>
                }
              </button>
            </form>

            <footer class="auth-card-footer">
              <a routerLink="/sign-in" class="auth-back-link">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <line x1="19" y1="12" x2="5" y2="12"/>
                  <polyline points="12 19 5 12 12 5"/>
                </svg>
                <span>Back to sign in</span>
              </a>
            </footer>
          } @else {
            <!-- State: SENT -->
            <div class="auth-sent-content">
              <div class="auth-sent-icon-wrap" aria-hidden="true">
                <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="#fb923c" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M22 13V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h9"/>
                  <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
                  <path d="m16 19 2 2 4-4"/>
                </svg>
              </div>

              <h1 class="auth-card-title">Check your inbox</h1>
              <p class="auth-card-subtitle">
                We've sent a password reset link to<br>
                <strong class="email-highlight">{{ email }}</strong>
              </p>

              <div class="auth-sent-tips">
                <div class="tip-item">
                  <span class="tip-dot">1</span>
                  <span>Click the reset link inside the email</span>
                </div>
                <div class="tip-item">
                  <span class="tip-dot">2</span>
                  <span>Set your new secure password</span>
                </div>
                <div class="tip-item">
                  <span class="tip-dot">3</span>
                  <span>Return and sign in with your new credentials</span>
                </div>
              </div>

              <!-- Mailpit / Inbox Shortcut -->
              <a
                href="http://localhost:8025"
                target="_blank"
                rel="noopener"
                class="btn-primary btn-inbox-link">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <polyline points="22 12 16 12 14 15 10 15 8 12 2 12"/>
                  <path d="M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/>
                </svg>
                <span>Open Mailpit Inbox (Dev)</span>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
                  <polyline points="15 3 21 3 21 9"/>
                  <line x1="10" y1="14" x2="21" y2="3"/>
                </svg>
              </a>

              <div class="auth-resend-row">
                <span class="auth-resend-text">Didn't receive the email?</span>
                <button
                  type="button"
                  class="btn-resend"
                  [disabled]="resendCooldown() > 0"
                  (click)="resendEmail()">
                  @if (resendCooldown() > 0) {
                    Resend in {{ resendCooldown() }}s
                  } @else {
                    Resend email
                  }
                </button>
              </div>

              <footer class="auth-card-footer mt-4">
                <a routerLink="/sign-in" class="auth-back-link">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <line x1="19" y1="12" x2="5" y2="12"/>
                    <polyline points="12 19 5 12 12 5"/>
                  </svg>
                  <span>Back to sign in</span>
                </a>
              </footer>
            </div>
          }

        </div>
      </main>
    </div>
  `,
  styles: [`
    .auth-root {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      background: #0f172a;
      color: #f8fafc;
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
      padding: 1.5rem;
      overflow: hidden;
    }

    .auth-bg {
      position: fixed;
      inset: 0;
      pointer-events: none;
      z-index: 0;
    }

    .auth-bg-orb {
      position: absolute;
      border-radius: 9999px;
      filter: blur(90px);
    }
    .auth-bg-orb--1 {
      width: 500px; height: 500px;
      background: radial-gradient(circle, rgba(251, 146, 60, 0.25) 0%, transparent 70%);
      top: -150px; left: -100px;
    }
    .auth-bg-orb--2 {
      width: 450px; height: 450px;
      background: radial-gradient(circle, rgba(234, 88, 12, 0.2) 0%, transparent 70%);
      bottom: -100px; right: -50px;
    }
    .auth-bg-grid {
      position: absolute;
      inset: 0;
      background-image:
        linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
        linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
      background-size: 32px 32px;
    }

    .auth-main {
      position: relative;
      z-index: 1;
      width: 100%;
      max-width: 440px;
    }

    .auth-card {
      background: rgba(30, 41, 59, 0.75);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border: 1px solid rgba(255, 255, 255, 0.1);
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(255, 255, 255, 0.05);
      border-radius: 1.25rem;
      padding: 2.25rem;
      display: flex;
      flex-direction: column;
    }

    .auth-brand-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 1.5rem;
    }
    .auth-brand-title {
      font-size: 1.125rem;
      font-weight: 700;
      color: #ffffff;
      letter-spacing: -0.02em;
    }

    .auth-card-header {
      margin-bottom: 1.75rem;
    }
    .auth-card-title {
      font-size: 1.5rem;
      font-weight: 700;
      color: #ffffff;
      letter-spacing: -0.02em;
      margin: 0 0 0.5rem;
    }
    .auth-card-subtitle {
      font-size: 0.875rem;
      color: #94a3b8;
      line-height: 1.5;
      margin: 0;
    }

    .auth-form {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    .auth-field {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .auth-label {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #cbd5e1;
    }

    .auth-input-wrap {
      position: relative;
      display: flex;
      align-items: center;
    }
    .auth-input-icon {
      position: absolute;
      left: 0.875rem;
      width: 1.125rem;
      height: 1.125rem;
      color: #64748b;
      pointer-events: none;
    }
    .auth-input {
      width: 100%;
      height: 2.75rem;
      padding: 0 1rem 0 2.625rem;
      background: rgba(15, 23, 42, 0.6);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 0.625rem;
      color: #ffffff;
      font-size: 0.9375rem;
      outline: none;
      transition: all 0.15s ease;
    }
    .auth-input::placeholder {
      color: #64748b;
    }
    .auth-input:focus {
      border-color: #fb923c;
      box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.2);
      background: rgba(15, 23, 42, 0.85);
    }

    .auth-error {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.75rem 1rem;
      background: rgba(239, 68, 68, 0.1);
      border: 1px solid rgba(239, 68, 68, 0.25);
      border-radius: 0.5rem;
      color: #fca5a5;
      font-size: 0.8125rem;
    }

    .btn-primary {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      height: 2.75rem;
      width: 100%;
      padding: 0 1.25rem;
      background: linear-gradient(135deg, #fb923c 0%, #ea580c 100%);
      color: #ffffff;
      font-weight: 600;
      font-size: 0.9375rem;
      border: none;
      border-radius: 0.625rem;
      cursor: pointer;
      text-decoration: none;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      box-shadow: 0 4px 12px rgba(234, 88, 12, 0.35);
    }
    .btn-primary:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 6px 16px rgba(234, 88, 12, 0.45);
      filter: brightness(1.05);
    }
    .btn-primary:active:not(:disabled) {
      transform: translateY(0);
    }
    .btn-primary:disabled {
      opacity: 0.5;
      cursor: not-allowed;
      box-shadow: none;
    }

    .btn-spinner {
      width: 1.125rem;
      height: 1.125rem;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: #ffffff;
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .auth-card-footer {
      margin-top: 1.5rem;
      display: flex;
      justify-content: center;
    }
    .auth-back-link {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      color: #94a3b8;
      font-size: 0.8125rem;
      font-weight: 500;
      text-decoration: none;
      transition: color 0.15s ease;
    }
    .auth-back-link:hover {
      color: #fb923c;
    }

    /* ── Sent State Styles ────────────────────────────────────────── */
    .auth-sent-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
    }
    .auth-sent-icon-wrap {
      width: 4rem;
      height: 4rem;
      border-radius: 1rem;
      background: rgba(251, 146, 60, 0.12);
      border: 1px solid rgba(251, 146, 60, 0.25);
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 1.25rem;
      box-shadow: 0 0 20px rgba(251, 146, 60, 0.15);
    }

    .email-highlight {
      color: #fb923c;
      word-break: break-all;
    }

    .auth-sent-tips {
      width: 100%;
      background: rgba(15, 23, 42, 0.5);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 0.75rem;
      padding: 1rem;
      margin: 1.25rem 0 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      text-align: left;
    }
    .tip-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      font-size: 0.8125rem;
      color: #cbd5e1;
    }
    .tip-dot {
      width: 1.375rem;
      height: 1.375rem;
      border-radius: 50%;
      background: rgba(251, 146, 60, 0.15);
      color: #fb923c;
      font-weight: 700;
      font-size: 0.75rem;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .btn-inbox-link {
      margin-bottom: 1.25rem;
    }

    .auth-resend-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.8125rem;
      color: #94a3b8;
    }
    .btn-resend {
      background: none;
      border: none;
      color: #fb923c;
      font-weight: 600;
      font-size: 0.8125rem;
      cursor: pointer;
      padding: 0;
      text-decoration: underline;
    }
    .btn-resend:disabled {
      color: #64748b;
      cursor: not-allowed;
      text-decoration: none;
    }
    .mt-4 { margin-top: 1rem; }
  `],
})
export class ForgotPasswordPageComponent {
  private readonly keycloakApi = inject(KeycloakApiService);

  readonly state = signal<ForgotState>('input');
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly resendCooldown = signal(0);

  email = '';
  private resendTimer: ReturnType<typeof setInterval> | null = null;

  get isValidEmail(): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email.trim());
  }

  sendResetEmail(): void {
    if (!this.isValidEmail) return;
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.keycloakApi.sendPasswordResetEmail(this.email).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.state.set('sent');
        this.startCooldown();
      },
      error: (err: Error) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.message || 'Unable to send reset email. Please try again.');
      },
    });
  }

  resendEmail(): void {
    if (this.resendCooldown() > 0) return;
    this.startCooldown();
    this.keycloakApi.sendPasswordResetEmail(this.email).subscribe();
  }

  private startCooldown(): void {
    this.resendCooldown.set(60);
    if (this.resendTimer) clearInterval(this.resendTimer);
    this.resendTimer = setInterval(() => {
      const v = this.resendCooldown() - 1;
      this.resendCooldown.set(v);
      if (v <= 0 && this.resendTimer) {
        clearInterval(this.resendTimer);
        this.resendTimer = null;
      }
    }, 1000);
  }
}
