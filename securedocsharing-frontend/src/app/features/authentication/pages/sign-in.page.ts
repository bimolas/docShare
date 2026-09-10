import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  OnInit,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { KeycloakService } from '../../../core/auth/keycloak.service';
import { KeycloakApiService } from '../../../core/auth/keycloak-api.service';
import { AuthIntentStorage } from '../../../core/auth/auth-intent.storage';
import { AuthStore } from '../../../state/auth/auth.store';


/**
 * SignInPageComponent: Premium branded sign-in page.
 * Provides Keycloak OIDC redirect sign-in and Google SSO.
 * Routes users to sign-up for new registrations.
 */
@Component({
  selector: 'app-sign-in-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <div class="auth-root">
      <!-- Background decoration -->
      <div class="auth-bg">
        <div class="auth-bg-orb auth-bg-orb--1"></div>
        <div class="auth-bg-orb auth-bg-orb--2"></div>
        <div class="auth-bg-grid"></div>
      </div>

      <!-- Left branding panel (desktop only) -->
      <aside class="auth-brand-panel" aria-hidden="true">
        <div class="auth-brand-inner">
          <div class="auth-logo-mark">
            <svg width="36" height="36" viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="36" height="36" rx="10" fill="#fb923c"/>
              <path d="M10 14a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v2a4 4 0 0 1-4 4h-8a4 4 0 0 1-4-4v-2Z" fill="white" opacity="0.9"/>
              <rect x="10" y="22" width="16" height="4" rx="2" fill="white" opacity="0.5"/>
            </svg>
          </div>
          <h1 class="auth-brand-name">SecureDocSharing</h1>
          <p class="auth-brand-tagline">Enterprise-grade document security and collaboration.</p>

          <ul class="auth-feature-list" role="list">
            <li class="auth-feature-item">
              <span class="auth-feature-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              </span>
              <span>End-to-end encrypted file sharing</span>
            </li>
            <li class="auth-feature-item">
              <span class="auth-feature-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
              </span>
              <span>Granular permission management</span>
            </li>
            <li class="auth-feature-item">
              <span class="auth-feature-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
              </span>
              <span>Full audit trail and analytics</span>
            </li>
            <li class="auth-feature-item">
              <span class="auth-feature-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
              </span>
              <span>Time-limited secure sharing links</span>
            </li>
          </ul>

          <div class="auth-brand-quote">
            <p>"SecureDocSharing transformed how our team handles sensitive contracts. Zero compromises on security."</p>
            <div class="auth-brand-quote-author">
              <div class="auth-quote-avatar">JM</div>
              <div>
                <p class="auth-quote-name">James Mitchell</p>
                <p class="auth-quote-title">CTO, Nexatech Corp</p>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <!-- Main auth card -->
      <main class="auth-main">
        <div class="auth-card" role="main">
          <!-- Logo (mobile only) -->
          <div class="auth-mobile-logo" aria-hidden="true">
            <svg width="28" height="28" viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="36" height="36" rx="10" fill="#fb923c"/>
              <path d="M10 14a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v2a4 4 0 0 1-4 4h-8a4 4 0 0 1-4-4v-2Z" fill="white" opacity="0.9"/>
              <rect x="10" y="22" width="16" height="4" rx="2" fill="white" opacity="0.5"/>
            </svg>
          </div>

          <header class="auth-card-header">
            <h2 class="auth-card-title">Welcome back</h2>
            <p class="auth-card-subtitle">Sign in to your SecureDocSharing account</p>
          </header>

          <!-- Google SSO -->
          <button
            id="btn-google-signin"
            class="btn-social btn-google"
            type="button"
            (click)="signInWithGoogle()"
            [disabled]="isLoading()"
            aria-label="Sign in with Google">
            <svg width="18" height="18" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
              <path d="M17.64 9.2a10.34 10.34 0 0 0-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92a8.78 8.78 0 0 0 2.68-6.62Z" fill="#4285F4"/>
              <path d="M9 18a8.59 8.59 0 0 0 5.96-2.18l-2.91-2.26A5.43 5.43 0 0 1 9 14.7a5.41 5.41 0 0 1-5.08-3.74H.96v2.33A9 9 0 0 0 9 18Z" fill="#34A853"/>
              <path d="M3.92 10.96A5.44 5.44 0 0 1 3.63 9a5.44 5.44 0 0 1 .29-.96V5.71H.96A9 9 0 0 0 0 9a9 9 0 0 0 .96 3.29l2.96-2.33Z" fill="#FBBC05"/>
              <path d="M9 3.58a4.86 4.86 0 0 1 3.44 1.35l2.58-2.58A8.64 8.64 0 0 0 9 0 9 9 0 0 0 .96 5.71L3.92 8.04A5.41 5.41 0 0 1 9 3.58Z" fill="#EA4335"/>
            </svg>
            <span>Continue with Google</span>
          </button>

          <div class="auth-divider" role="separator">
            <span class="auth-divider-text">or continue with email</span>
          </div>

          <!-- Email/password form -->
          <form id="form-signin" (ngSubmit)="signInWithEmail()" novalidate>

            <div class="auth-field">
              <label class="auth-label" for="signin-email">Email address</label>
              <div class="auth-input-wrap">
                <svg class="auth-input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
                <input
                  id="signin-email"
                  name="email"
                  type="email"
                  class="auth-input"
                  [(ngModel)]="email"
                  placeholder="you@company.com"
                  autocomplete="username"
                  autofocus
                  required
                  [disabled]="isLoading()" />
              </div>
            </div>

            <div class="auth-field">
              <label class="auth-label" for="signin-password">
                <span>Password</span>
                <a routerLink="/forgot-password" class="auth-label-link">Forgot password?</a>
              </label>
              <div class="auth-input-wrap">
                <svg class="auth-input-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect width="18" height="11" x="3" y="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
                <input
                  id="signin-password"
                  name="password"
                  [type]="showPassword() ? 'text' : 'password'"
                  class="auth-input"
                  [(ngModel)]="password"
                  placeholder="Enter your password"
                  autocomplete="current-password"
                  required
                  [disabled]="isLoading()" />
                <button type="button" class="auth-eye" (click)="showPassword.set(!showPassword())" [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'">
                  @if (showPassword()) {
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
                  } @else {
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  }
                </button>
              </div>
            </div>

            <button
              id="btn-email-signin"
              class="btn-primary"
              type="submit"
              [disabled]="isLoading() || !email || !password"
              aria-live="polite">
              @if (isLoading() && !isGoogleLoading()) {
                <span class="btn-spinner" aria-hidden="true"></span>
                <span>Signing in…</span>
              } @else {
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/></svg>
                <span>Sign in</span>
              }
            </button>
          </form>

          @if (isUnverifiedEmail()) {
            <div class="unverified-banner" role="alert">
              <div class="unverified-header">
                <div class="unverified-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect width="20" height="16" x="2" y="4" rx="2"/>
                    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
                  </svg>
                </div>
                <div>
                  <h4 class="unverified-title">Account Activation Pending</h4>
                  <p class="unverified-desc">
                    Your email address <strong>{{ unverifiedEmailAddress() }}</strong> requires verification before you can sign in.
                  </p>
                </div>
              </div>

              <div class="unverified-actions">
                <button
                  type="button"
                  class="btn-resend"
                  (click)="resendVerificationEmail()"
                  [disabled]="isResendingEmail()">
                  @if (isResendingEmail()) {
                    <span class="btn-spinner" aria-hidden="true"></span>
                    <span>Sending…</span>
                  } @else {
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <polyline points="23 4 23 10 17 10"/>
                      <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                    </svg>
                    <span>Resend Verification Email</span>
                  }
                </button>

                <a
                  href="http://localhost:8025"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="btn-mailpit"
                  title="Open Mailpit Webmail (Dev Environment)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect width="20" height="16" x="2" y="4" rx="2"/>
                    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/>
                  </svg>
                  <span>Open Mailpit Inbox</span>
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
                    <polyline points="15 3 21 3 21 9"/>
                    <line x1="10" y1="14" x2="21" y2="3"/>
                  </svg>
                </a>
              </div>

              @if (resendSuccessMessage()) {
                <div class="unverified-success">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#16a34a" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
                  <span>{{ resendSuccessMessage() }}</span>
                </div>
              }
            </div>
          }

          @if (errorMessage() && !isUnverifiedEmail()) {
            <div class="auth-error" role="alert" aria-live="assertive">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
              <span>{{ errorMessage() }}</span>
            </div>
          }

          <footer class="auth-card-footer">
            <p class="auth-footer-text">
              Don't have an account?
              <a id="link-signup" routerLink="/sign-up" class="auth-link">Create one for free</a>
            </p>
          </footer>
        </div>

        <p class="auth-legal">
          By signing in, you agree to our
          <a href="#" class="auth-link-subtle">Terms of Service</a> and
          <a href="#" class="auth-link-subtle">Privacy Policy</a>.
        </p>
      </main>
    </div>
  `,
  styles: [`
    /* ── Root layout ─────────────────────────────────────────────── */
    .auth-root {
      min-height: 100vh;
      display: flex;
      position: relative;
      background: #f8fafc;
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
      overflow: hidden;
    }

    /* ── Background decoration ────────────────────────────────────── */
    .auth-bg {
      position: fixed;
      inset: 0;
      pointer-events: none;
      z-index: 0;
    }
    .auth-bg-orb {
      position: absolute;
      border-radius: 9999px;
      filter: blur(80px);
      opacity: 0.35;
    }
    .auth-bg-orb--1 {
      width: 600px; height: 600px;
      background: radial-gradient(circle, #ff5500 0%, transparent 70%);
      top: -200px; left: -100px;
      opacity: 0.08;
    }
    .auth-bg-orb--2 {
      width: 500px; height: 500px;
      background: radial-gradient(circle, #ff5500 0%, transparent 70%);
      bottom: -100px; right: 0;
      opacity: 0.06;
    }
    .auth-bg-grid {
      position: absolute;
      inset: 0;
      background-image:
        linear-gradient(rgba(15, 23, 42, 0.025) 1px, transparent 1px),
        linear-gradient(90deg, rgba(15, 23, 42, 0.025) 1px, transparent 1px);
      background-size: 40px 40px;
    }

    /* ── Brand panel (left — desktop) ─────────────────────────────── */
    .auth-brand-panel {
      display: none;
      width: 480px;
      flex-shrink: 0;
      background: linear-gradient(150deg, #fb923c 0%, #ea580c 100%);
      position: relative;
      z-index: 1;
      overflow: hidden;
    }
    .auth-brand-panel::before {
      content: '';
      position: absolute;
      inset: 0;
      background:
        radial-gradient(circle at 30% 20%, rgba(255,255,255,0.06) 0%, transparent 60%),
        radial-gradient(circle at 80% 80%, rgba(0,0,0,0.15) 0%, transparent 60%);
    }
    .auth-brand-inner {
      position: relative;
      z-index: 1;
      padding: 3rem 2.5rem;
      height: 100%;
      display: flex;
      flex-direction: column;
    }
    .auth-logo-mark {
      margin-bottom: 1.5rem;
    }
    .auth-logo-mark svg rect:first-child {
      fill: rgba(255,255,255,0.15);
    }
    .auth-brand-name {
      font-size: 1.5rem;
      font-weight: 700;
      color: #ffffff;
      margin: 0 0 0.5rem;
      letter-spacing: -0.02em;
    }
    .auth-brand-tagline {
      font-size: 0.9375rem;
      color: rgba(255,255,255,0.75);
      margin: 0 0 3rem;
      line-height: 1.6;
    }
    .auth-feature-list {
      list-style: none;
      padding: 0;
      margin: 0 0 auto;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .auth-feature-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      font-size: 0.875rem;
      color: rgba(255,255,255,0.9);
      line-height: 1.5;
    }
    .auth-feature-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 28px; height: 28px;
      background: rgba(255,255,255,0.12);
      border-radius: 8px;
      flex-shrink: 0;
      color: rgba(255,255,255,0.9);
    }

    /* ── Brand quote ──────────────────────────────────────────────── */
    .auth-brand-quote {
      margin-top: 3rem;
      padding: 1.25rem;
      background: rgba(255,255,255,0.08);
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 12px;
    }
    .auth-brand-quote > p {
      font-size: 0.875rem;
      color: rgba(255,255,255,0.85);
      line-height: 1.6;
      margin: 0 0 1rem;
      font-style: italic;
    }
    .auth-brand-quote-author {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .auth-quote-avatar {
      width: 36px; height: 36px;
      border-radius: 50%;
      background: rgba(255,255,255,0.2);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.75rem;
      font-weight: 600;
      color: white;
      flex-shrink: 0;
    }
    .auth-quote-name {
      font-size: 0.875rem;
      font-weight: 600;
      color: white;
      margin: 0;
    }
    .auth-quote-title {
      font-size: 0.75rem;
      color: rgba(255,255,255,0.6);
      margin: 0;
    }

    /* ── Main content area ────────────────────────────────────────── */
    .auth-main {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 2rem 1rem;
      position: relative;
      z-index: 1;
    }

    /* ── Auth card ────────────────────────────────────────────────── */
    .auth-card {
      width: 100%;
      max-width: 420px;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 16px;
      padding: 2.5rem;
      box-shadow:
        0 1px 3px rgba(15, 23, 42, 0.06),
        0 4px 24px rgba(15, 23, 42, 0.06);
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .auth-mobile-logo {
      display: flex;
      align-items: center;
      margin-bottom: -0.25rem;
    }

    /* ── Header ───────────────────────────────────────────────────── */
    .auth-card-header {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
    }
    .auth-card-title {
      font-size: 1.5rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
      letter-spacing: -0.02em;
      line-height: 1.2;
    }
    .auth-card-subtitle {
      font-size: 0.875rem;
      color: #64748b;
      margin: 0;
      line-height: 1.5;
    }

    /* ── Social button ────────────────────────────────────────────── */
    .btn-social {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.625rem;
      width: 100%;
      height: 44px;
      padding: 0 1rem;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      font-size: 0.875rem;
      font-weight: 500;
      color: #0f172a;
      cursor: pointer;
      transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
      font-family: inherit;
    }
    .btn-social:hover:not(:disabled) {
      border-color: #cbd5e1;
      background: #f8fafc;
      box-shadow: 0 1px 4px rgba(15,23,42,0.06);
    }
    .btn-social:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    /* ── Divider ──────────────────────────────────────────────────── */
    .auth-divider {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      color: #94a3b8;
      font-size: 0.75rem;
    }
    .auth-divider::before,
    .auth-divider::after {
      content: '';
      flex: 1;
      height: 1px;
      background: #e2e8f0;
    }
    .auth-divider-text {
      white-space: nowrap;
    }

    /* ── Keycloak info block ───────────────────────────────────────── */
    .auth-keycloak-info {
      padding: 0.875rem 1rem;
      background: #f8fafc;
      border: 1px solid #f1f5f9;
      border-radius: 10px;
    }
    .auth-keycloak-desc {
      font-size: 0.8125rem;
      color: #64748b;
      margin: 0;
      line-height: 1.55;
      text-align: center;
    }

    /* ── Primary button ───────────────────────────────────────────── */
    .btn-primary {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      width: 100%;
      height: 44px;
      padding: 0 1rem;
      background: #fb923c;
      color: #ffffff;
      border: none;
      border-radius: 10px;
      font-size: 0.875rem;
      font-weight: 600;
      cursor: pointer;
      transition: background 0.15s ease, box-shadow 0.15s ease, transform 0.1s ease;
      font-family: inherit;
    }
    .btn-primary:hover:not(:disabled) {
      background: #ea580c;
      box-shadow: 0 4px 12px rgba(249, 115, 22, 0.28);
      transform: translateY(-1px);
    }
    .btn-primary:active:not(:disabled) {
      background: #c2410c;
      transform: translateY(0);
      box-shadow: none;
    }
    .btn-primary:disabled {
      opacity: 0.7;
      cursor: not-allowed;
    }

    /* ── Spinner ──────────────────────────────────────────────────── */
    .btn-spinner {
      width: 16px; height: 16px;
      border: 2px solid rgba(255,255,255,0.35);
      border-top-color: #ffffff;
      border-radius: 50%;
      animation: spin 0.7s linear infinite;
      flex-shrink: 0;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    /* ── Error ────────────────────────────────────────────────────── */
    .auth-error {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.75rem 1rem;
      background: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 10px;
      font-size: 0.8125rem;
      color: #dc2626;
      line-height: 1.4;
    }

    /* ── Form fields ──────────────────────────────────────────────── */
    form { display: flex; flex-direction: column; gap: 1rem; }

    .auth-field { display: flex; flex-direction: column; gap: 0.375rem; }

    .auth-label {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      font-size: 0.8125rem;
      font-weight: 600;
      color: #374151;
      letter-spacing: 0.01em;
    }
    .auth-label-link {
      font-size: 0.8125rem;
      font-weight: 400;
      color: #ea580c;
      text-decoration: none;
      transition: color 0.15s;
    }
    .auth-label-link:hover { color: #c2410c; text-decoration: underline; }

    .auth-input-wrap {
      position: relative;
      display: flex;
      align-items: center;
    }
    .auth-input-icon {
      position: absolute;
      left: 0.875rem;
      width: 1rem;
      height: 1rem;
      color: #9ca3af;
      pointer-events: none;
      flex-shrink: 0;
    }
    .auth-input {
      width: 100%;
      padding: 0.75rem 1rem 0.75rem 2.625rem;
      border: 1.5px solid #e5e7eb;
      border-radius: 10px;
      font-size: 0.9375rem;
      font-family: inherit;
      color: #111827;
      background: #fafafa;
      transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
      outline: none;
      box-sizing: border-box;
    }
    .auth-input:focus {
      border-color: #ea580c;
      background: #fff;
      box-shadow: 0 0 0 3px rgba(234,88,12,0.12);
    }
    .auth-input::placeholder { color: #9ca3af; }
    .auth-input:disabled { opacity: 0.55; cursor: not-allowed; }

    /* Extra padding-right when eye button is present */
    .auth-input-wrap .auth-eye ~ .auth-input,
    .auth-input-wrap .auth-input:has(~ .auth-eye) {
      padding-right: 2.75rem;
    }
    /* Works universally: just add right padding via a modifier when eye exists */
    .auth-input-wrap:has(.auth-eye) .auth-input {
      padding-right: 2.75rem;
    }

    .auth-eye {
      position: absolute;
      right: 0.75rem;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0.25rem;
      background: none;
      border: none;
      cursor: pointer;
      color: #9ca3af;
      transition: color 0.15s;
      border-radius: 4px;
    }
    .auth-eye:hover { color: #6b7280; }
    .auth-eye svg { width: 1rem; height: 1rem; }


    /* ── Footer ───────────────────────────────────────────────────── */
    .auth-card-footer {
      text-align: center;
      padding-top: 0.25rem;
      border-top: 1px solid #f1f5f9;
    }
    .auth-footer-text {
      font-size: 0.875rem;
      color: #64748b;
      margin: 0;
    }
    .auth-link {
      color: #ea580c;
      font-weight: 500;
      text-decoration: none;
      transition: color 0.15s;
    }
    .auth-link:hover { color: #c2410c; text-decoration: underline; }
    .auth-link-subtle {
      color: #64748b;
      text-decoration: none;
      font-size: 0.8125rem;
    }
    .auth-link-subtle:hover { color: #475569; text-decoration: underline; }

    /* ── Legal text ───────────────────────────────────────────────── */
    .auth-legal {
      margin-top: 1.25rem;
      font-size: 0.75rem;
      color: #94a3b8;
      text-align: center;
      max-width: 420px;
      line-height: 1.5;
    }

    /* ── Unverified email banner ───────────────────────────────────── */
    .unverified-banner {
      margin-top: 1.25rem;
      padding: 1.25rem;
      background: rgba(255, 247, 237, 0.95);
      border: 1px solid #ffedd5;
      border-left: 4px solid #f97316;
      border-radius: 12px;
      display: flex;
      flex-direction: column;
      gap: 1rem;
      box-shadow: 0 4px 12px rgba(249, 115, 22, 0.08);
      animation: fadeIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    }
    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(-4px); }
      to { opacity: 1; transform: translateY(0); }
    }
    .unverified-header {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
    }
    .unverified-icon {
      width: 36px;
      height: 36px;
      border-radius: 10px;
      background: #ffedd5;
      color: #ea580c;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .unverified-title {
      font-size: 0.9375rem;
      font-weight: 600;
      color: #9a3412;
      margin: 0 0 0.25rem 0;
    }
    .unverified-desc {
      font-size: 0.8125rem;
      color: #c2410c;
      margin: 0;
      line-height: 1.45;
    }
    .unverified-actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.625rem;
    }
    .btn-resend {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 0.875rem;
      background: #ea580c;
      color: #ffffff;
      font-size: 0.8125rem;
      font-weight: 600;
      border: none;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .btn-resend:hover:not(:disabled) {
      background: #c2410c;
      box-shadow: 0 2px 8px rgba(234, 88, 12, 0.25);
    }
    .btn-resend:disabled {
      opacity: 0.65;
      cursor: not-allowed;
    }
    .btn-mailpit {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 0.875rem;
      background: #ffffff;
      color: #475569;
      font-size: 0.8125rem;
      font-weight: 500;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      text-decoration: none;
      transition: all 0.15s ease;
    }
    .btn-mailpit:hover {
      background: #f8fafc;
      color: #0f172a;
      border-color: #94a3b8;
    }
    .unverified-success {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.8125rem;
      color: #15803d;
      background: #f0fdf4;
      padding: 0.5rem 0.75rem;
      border-radius: 6px;
      border: 1px solid #bbf7d0;
    }

    /* ── Responsive ───────────────────────────────────────────────── */
    @media (min-width: 1024px) {
      .auth-brand-panel { display: flex; }
      .auth-mobile-logo { display: none; }
      .auth-main { padding: 2rem 3rem; }
    }
    @media (max-width: 1023px) {
      .auth-card { padding: 2rem 1.5rem; }
    }
    @media (max-width: 480px) {
      .auth-card { padding: 1.5rem 1.25rem; border-radius: 12px; }
      .auth-card-title { font-size: 1.375rem; }
    }
  `],
})
export class SignInPageComponent implements OnInit {
  private readonly keycloakService = inject(KeycloakService);
  private readonly keycloakApi = inject(KeycloakApiService);
  private readonly authStore = inject(AuthStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  // ── Form state ─────────────────────────────────────────────────────────────
  email = '';
  password = '';
  readonly showPassword = signal(false);

  // ── UI state ───────────────────────────────────────────────────────────────
  readonly isLoading = signal(false);
  readonly isGoogleLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly isUnverifiedEmail = signal(false);
  readonly unverifiedEmailAddress = signal('');
  readonly isResendingEmail = signal(false);
  readonly resendSuccessMessage = signal<string | null>(null);

  ngOnInit(): void {
    const loggedIn = this.keycloakService.isLoggedIn();
    const hasToken = !!this.keycloakService.getToken();

    if (loggedIn && hasToken) {
      // Genuine active session — redirect to app.
      const target = this.getReturnUrl() || '/w/personal/dashboard';
      this.router.navigateByUrl(target, { replaceUrl: true });
      return;
    }

    if (loggedIn && !hasToken) {
      // Phantom auth: authenticated flag set but no access token.
      // Clear the stale state so the sign-in form renders and works correctly.
      console.warn('[SignInPage] Phantom auth state detected. Clearing before rendering sign-in form.');
      this.keycloakService.clearTokensAndState();
    }

    // Surface any init-time error (e.g. "Your previous session expired")
    const err = this.keycloakService.initError();
    if (err) {
      this.errorMessage.set(err);
      this.keycloakService.clearInitError();
    }
  }

  // ── Email + password sign-in (Keycloak ROPC grant) ─────────────────────────
  signInWithEmail(): void {
    if (!this.email || !this.password) return;
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.isUnverifiedEmail.set(false);
    this.resendSuccessMessage.set(null);

    this.keycloakApi.signIn(this.email, this.password).subscribe({
      next: (tokens) => {
        // Hand the tokens to KeycloakService so the rest of the app
        // (guards, interceptors, AuthStore) sees an authenticated session.
        this.keycloakService.setTokensFromRopc(tokens);
        this.authStore.loadUserProfile().subscribe({
          next: () => {
            const destination = this.getReturnUrl() || '/w/personal/dashboard';
            this.router.navigateByUrl(destination, { replaceUrl: true });
          },
          error: () => {
            const destination = this.getReturnUrl() || '/w/personal/dashboard';
            this.router.navigateByUrl(destination, { replaceUrl: true });
          },
        });
      },
      error: (err: Error) => {
        this.isLoading.set(false);
        if (err.message && err.message.startsWith('EMAIL_NOT_VERIFIED:')) {
          this.isUnverifiedEmail.set(true);
          this.unverifiedEmailAddress.set(this.email.trim());
        } else {
          this.errorMessage.set(err.message);
        }
      },
    });
  }

  // ── Resend Verification Email ──────────────────────────────────────────────
  resendVerificationEmail(): void {
    const targetEmail = this.unverifiedEmailAddress() || this.email.trim();
    if (!targetEmail) return;

    this.isResendingEmail.set(true);
    this.resendSuccessMessage.set(null);

    this.keycloakApi.sendVerificationEmail(targetEmail).subscribe({
      next: () => {
        this.isResendingEmail.set(false);
        this.resendSuccessMessage.set(`Verification email sent to ${targetEmail}. Please check your inbox or Mailpit.`);
      },
      error: (err: Error) => {
        this.isResendingEmail.set(false);
        this.errorMessage.set(err.message || 'Failed to resend verification email.');
      },
    });
  }

  // ── Google SSO (redirect flow — unchanged) ─────────────────────────────────
  async signInWithGoogle(): Promise<void> {
    this.isGoogleLoading.set(true);
    this.isLoading.set(true);
    this.errorMessage.set(null);
    try {
      this.saveReturnUrl();
      await this.keycloakService.login({
        idpHint: 'google',
        redirectUri: this.getCallbackUri(),
      });
    } catch {
      this.errorMessage.set('Unable to reach the authentication server. Please try again.');
      this.isGoogleLoading.set(false);
      this.isLoading.set(false);
    }
  }

  // ── Helpers ────────────────────────────────────────────────────────────────
  private getReturnUrl(): string | null {
    const returnUrl = this.route.snapshot.queryParams['returnUrl'];
    if (
      returnUrl &&
      typeof returnUrl === 'string' &&
      returnUrl.startsWith('/') &&
      !returnUrl.startsWith('/sign-in') &&
      !returnUrl.startsWith('/sign-up')
    ) {
      return returnUrl;
    }
    return null;
  }

  private getCallbackUri(): string {
    return window.location.origin + '/auth/callback';
  }

  private saveReturnUrl(): void {
    const returnUrl = this.getReturnUrl() || '/w/personal/dashboard';
    AuthIntentStorage.setReturnUrl(returnUrl);
  }
}
