import {
  Component,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  inject,
  OnInit,
  OnDestroy,
  signal,
  computed,
} from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { KeycloakService } from '../../../core/auth/keycloak.service';
import { AuthIntentStorage } from '../../../core/auth/auth-intent.storage';

type CallbackPhase =
  | 'verifying'   // code exchange in-flight
  | 'setting-up'  // authenticated — preparing workspace
  | 'welcoming'   // final flash before navigation
  | 'error';      // timed-out or auth failed

const PHASE_MESSAGES: Record<CallbackPhase, string> = {
  verifying:     'Verifying your identity…',
  'setting-up':  'Setting up your workspace…',
  welcoming:     'Almost there…',
  error:         'Something went wrong',
};

/**
 * AuthCallbackPageComponent
 *
 * OIDC redirect URI handler. Uses the same light #f8fafc background,
 * orange-orb decoration and grid as the Sign-In page so the brand
 * experience is seamless. The real logo.svg from /public is used.
 *
 * Phases:
 *  1. verifying  — waiting for Keycloak init (done in APP_INITIALIZER)
 *  2. setting-up — code exchanged OK, brief UI animation
 *  3. welcoming  — navigating to destination
 *
 * Safety timeout: 10 s → shows recoverable error UI instead of spinning.
 */
@Component({
  selector: 'app-auth-callback-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule],
  template: `
    <div class="cb-root" [class.cb-has-error]="phase() === 'error'" aria-live="polite">

      <!-- ── Background (matches sign-in page) ── -->
      <div class="cb-bg" aria-hidden="true">
        <div class="cb-orb cb-orb--1"></div>
        <div class="cb-orb cb-orb--2"></div>
        <div class="cb-grid"></div>
      </div>

      <!-- ── Center card ── -->
      <div class="cb-card" role="status">

        <!-- Logo -->
        <div class="cb-logo-wrap" [class.cb-logo-wrap--error]="phase() === 'error'">
          @if (phase() !== 'error') {
            <img src="/logo.svg" width="44" height="44" alt="SecureDocSharing" class="cb-logo-img" />
          } @else {
            <svg class="cb-error-icon" width="44" height="44" viewBox="0 0 24 24"
                 fill="none" stroke="#ef4444" stroke-width="1.5"
                 stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
          }
        </div>

        <!-- Brand name -->
        <div class="cb-brand">
          <span class="cb-brand-name">SecureDocSharing</span>
        </div>

        <!-- Status label -->
        <p class="cb-label" [class.cb-label--error]="phase() === 'error'">
          {{ phaseMessage() }}
        </p>

        @if (phase() !== 'error') {
          <!-- Step tracker -->
          <div class="cb-steps" role="progressbar" aria-label="Sign-in progress">
            <div class="cb-step" [class.active]="stepIndex() >= 0" [class.done]="stepIndex() > 0">
              <div class="step-dot">
                @if (stepIndex() > 0) {
                  <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                    <path d="M2 5l2 2 4-4" stroke="white" stroke-width="1.5"
                          stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                }
              </div>
              <span class="step-label">Verify identity</span>
            </div>

            <div class="step-connector" [class.active]="stepIndex() > 0"></div>

            <div class="cb-step" [class.active]="stepIndex() >= 1" [class.done]="stepIndex() > 1">
              <div class="step-dot">
                @if (stepIndex() > 1) {
                  <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                    <path d="M2 5l2 2 4-4" stroke="white" stroke-width="1.5"
                          stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                }
              </div>
              <span class="step-label">Set up workspace</span>
            </div>

            <div class="step-connector" [class.active]="stepIndex() > 1"></div>

            <div class="cb-step" [class.active]="stepIndex() >= 2">
              <div class="step-dot">
                @if (stepIndex() >= 2) {
                  <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                    <path d="M2 5l2 2 4-4" stroke="white" stroke-width="1.5"
                          stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                }
              </div>
              <span class="step-label">{{ isNewUser() ? 'Get started' : 'Enter app' }}</span>
            </div>
          </div>

          <!-- Arc progress ring -->
          <div class="cb-arc" aria-hidden="true">
            <svg class="arc-svg" viewBox="0 0 44 44" fill="none">
              <circle class="arc-track" cx="22" cy="22" r="18" stroke-width="3"/>
              <circle class="arc-fill" cx="22" cy="22" r="18" stroke-width="3"
                      stroke-linecap="round"
                      [style.stroke-dashoffset]="arcOffset()"/>
            </svg>
          </div>
        }

        @if (phase() === 'error') {
          <p class="cb-error-msg">{{ errorDetail() }}</p>
          <div class="cb-error-actions">
            <button class="btn-retry" (click)="retry()">Try again</button>
            <button class="btn-home"  (click)="goHome()">Back to sign in</button>
          </div>
        }

      </div><!-- /cb-card -->

    </div><!-- /cb-root -->
  `,
  styles: [`
    /* ── Root — light background matching sign-in ─────────────── */
    .cb-root {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f8fafc;
      position: relative;
      overflow: hidden;
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
    }

    /* ── Background decoration ─────────────────────────────────── */
    .cb-bg { position: fixed; inset: 0; pointer-events: none; z-index: 0; }

    .cb-orb {
      position: absolute;
      border-radius: 9999px;
      filter: blur(80px);
    }
    .cb-orb--1 {
      width: 600px; height: 600px;
      background: radial-gradient(circle, #ff5500 0%, transparent 70%);
      top: -200px; left: -100px;
      opacity: 0.07;
      animation: orbDrift 10s ease-in-out infinite alternate;
    }
    .cb-orb--2 {
      width: 500px; height: 500px;
      background: radial-gradient(circle, #ff5500 0%, transparent 70%);
      bottom: -100px; right: 0;
      opacity: 0.05;
      animation: orbDrift 14s ease-in-out infinite alternate-reverse;
    }
    @keyframes orbDrift {
      from { transform: translate(0, 0) scale(1); }
      to   { transform: translate(24px, 16px) scale(1.04); }
    }
    .cb-grid {
      position: absolute; inset: 0;
      background-image:
        linear-gradient(rgba(15,23,42,0.025) 1px, transparent 1px),
        linear-gradient(90deg, rgba(15,23,42,0.025) 1px, transparent 1px);
      background-size: 40px 40px;
    }

    /* ── Card ──────────────────────────────────────────────────── */
    .cb-card {
      position: relative;
      z-index: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 1.25rem;
      padding: 2.5rem 2rem;
      background: rgba(255, 255, 255, 0.8);
      backdrop-filter: blur(20px);
      -webkit-backdrop-filter: blur(20px);
      border: 1px solid rgba(15, 23, 42, 0.08);
      border-radius: 20px;
      width: 340px;
      box-shadow:
        0 1px 2px rgba(15,23,42,0.04),
        0 8px 24px rgba(15,23,42,0.08),
        0 24px 48px rgba(15,23,42,0.06);
      animation: cardIn 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
    }
    @keyframes cardIn {
      from { opacity: 0; transform: translateY(20px) scale(0.97); }
      to   { opacity: 1; transform: translateY(0)   scale(1); }
    }

    /* ── Logo wrap ─────────────────────────────────────────────── */
    .cb-logo-wrap {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 68px; height: 68px;
      border-radius: 18px;
      background: rgba(249, 115, 22, 0.08);
      border: 1px solid rgba(249, 115, 22, 0.18);
      animation: logoPulse 2.6s ease-in-out infinite;
    }
    .cb-logo-wrap--error {
      background: rgba(239, 68, 68, 0.08);
      border-color: rgba(239, 68, 68, 0.2);
      animation: none;
    }
    @keyframes logoPulse {
      0%, 100% { box-shadow: 0 0 0 0   rgba(249,115,22,0.2); }
      50%       { box-shadow: 0 0 0 10px rgba(249,115,22,0); }
    }
    .cb-logo-img { display: block; }

    /* ── Brand name ────────────────────────────────────────────── */
    .cb-brand { margin: -0.25rem 0 0; }
    .cb-brand-name {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #64748b;
      letter-spacing: 0.01em;
    }

    /* ── Status label ──────────────────────────────────────────── */
    .cb-label {
      font-size: 1rem;
      font-weight: 600;
      color: #1e293b;
      margin: 0;
      letter-spacing: -0.015em;
      text-align: center;
      transition: opacity 0.3s;
    }
    .cb-label--error { color: #dc2626; }

    /* ── Step tracker ──────────────────────────────────────────── */
    .cb-steps {
      display: flex;
      align-items: center;
      width: 100%;
      gap: 0;
    }
    .cb-step {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 5px;
      flex: 0 0 auto;
    }
    .step-dot {
      width: 22px; height: 22px;
      border-radius: 50%;
      border: 2px solid #e2e8f0;
      background: white;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.3s, border-color 0.3s;
    }
    .cb-step.active .step-dot {
      border-color: #fb923c;
      background: rgba(251, 146, 60, 0.1);
      animation: dotPulse 1.2s ease-in-out infinite;
    }
    .cb-step.done .step-dot {
      background: #fb923c;
      border-color: #fb923c;
      animation: none;
    }
    @keyframes dotPulse {
      0%, 100% { box-shadow: 0 0 0 0   rgba(251,146,60,0.35); }
      50%       { box-shadow: 0 0 0 5px rgba(251,146,60,0); }
    }
    .step-label {
      font-size: 0.6rem;
      color: #94a3b8;
      white-space: nowrap;
      font-weight: 500;
      transition: color 0.3s;
    }
    .cb-step.active .step-label { color: #fb923c; }
    .cb-step.done   .step-label { color: #f97316; }

    .step-connector {
      flex: 1;
      height: 1.5px;
      background: #e2e8f0;
      margin: 0 4px;
      margin-bottom: 16px; /* align with dot vertical center */
      transition: background 0.5s ease;
    }
    .step-connector.active { background: #fb923c; }

    /* ── Arc ring ──────────────────────────────────────────────── */
    .cb-arc { width: 48px; height: 48px; }
    .arc-svg {
      width: 100%; height: 100%;
      transform: rotate(-90deg);
    }
    .arc-track { stroke: #e2e8f0; }
    .arc-fill {
      stroke: #fb923c;
      stroke-dasharray: 113.1; /* 2π × 18 */
      stroke-dashoffset: 113.1;
      transition: stroke-dashoffset 0.9s cubic-bezier(0.4, 0, 0.2, 1);
    }

    /* ── Error UI ──────────────────────────────────────────────── */
    .cb-error-msg {
      font-size: 0.8125rem;
      color: #64748b;
      text-align: center;
      margin: 0;
      line-height: 1.55;
      max-width: 260px;
    }
    .cb-error-actions {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      width: 100%;
    }
    .btn-retry {
      width: 100%;
      padding: 0.65rem;
      border-radius: 10px;
      background: #fb923c;
      color: white;
      font-weight: 600;
      font-size: 0.875rem;
      border: none;
      cursor: pointer;
      transition: background 0.2s;
    }
    .btn-retry:hover { background: #f97316; }
    .btn-home {
      width: 100%;
      padding: 0.65rem;
      border-radius: 10px;
      background: transparent;
      color: #64748b;
      font-size: 0.875rem;
      font-weight: 500;
      border: 1px solid #e2e8f0;
      cursor: pointer;
      transition: background 0.2s, border-color 0.2s;
    }
    .btn-home:hover {
      background: #f1f5f9;
      border-color: #cbd5e1;
    }
  `],
})
export class AuthCallbackPageComponent implements OnInit, OnDestroy {
  private readonly keycloakService = inject(KeycloakService);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);

  // ── Reactive state ──────────────────────────────────────────────
  readonly phase      = signal<CallbackPhase>('verifying');
  readonly errorDetail = signal<string>('');
  readonly arcOffset  = signal<number>(113.1); // 2π × 18
  readonly stepIndex  = signal<number>(0);
  readonly isNewUser  = signal<boolean>(false);

  readonly phaseMessage = computed(() => PHASE_MESSAGES[this.phase()]);

  private timeoutHandle:  ReturnType<typeof setTimeout> | null = null;
  private progressHandle: ReturnType<typeof setTimeout> | null = null;

  private readonly ARC_FULL = 113.1;

  ngOnInit(): void {
    // Detect new-user registration flow immediately so the step label is right
    const registerFlow = AuthIntentStorage.isRegisterFlow();
    const pendingOnboard = AuthIntentStorage.isPendingOnboarding();
    this.isNewUser.set(registerFlow || pendingOnboard);

    // Safety net — 15 s hard timeout.
    // NOTE: This timeout is explicitly cancelled just before any navigation call
    // so that Angular view transitions (which keep this component in the DOM
    // during the route change) cannot accidentally trigger the error screen
    // after a successful sign-in.
    this.timeoutHandle = setTimeout(() => {
      if (this.phase() !== 'error') {
        this.showError(
          'Sign-in is taking longer than expected. Please try again.',
        );
      }
    }, 15_000);

    this.runCallbackFlow();
  }

  ngOnDestroy(): void {
    if (this.timeoutHandle)  clearTimeout(this.timeoutHandle);
    if (this.progressHandle) clearTimeout(this.progressHandle);
  }

  /**
   * Polls until KeycloakService.isInitialized() is true, or until the deadline
   * is reached. This handles the edge case where the APP_INITIALIZER is still
   * in-flight when the callback component mounts (e.g. slow Keycloak on Google
   * IdP redirect chains).
   */
  private waitForKeycloakInit(deadlineMs = 8000): Promise<boolean> {
    return new Promise((resolve) => {
      const start = Date.now();
      const check = () => {
        if (this.keycloakService.isInitialized()) {
          resolve(this.keycloakService.isLoggedIn());
          return;
        }
        if (Date.now() - start >= deadlineMs) {
          resolve(false);
          return;
        }
        this.progressHandle = setTimeout(check, 100);
      };
      check();
    });
  }

  private async runCallbackFlow(): Promise<void> {
    // Phase 1 — verifying.
    // Poll until Keycloak init resolves (handles slow Google IdP redirect chains
    // where keycloak.js code exchange takes longer than a fixed wait would allow).
    this.arcOffset.set(this.ARC_FULL * 0.6);
    this.cdr.markForCheck();

    const authenticated = await this.waitForKeycloakInit(8000);

    if (!authenticated) {
      const err = this.keycloakService.initError();
      this.showError(
        err ?? 'We couldn\'t verify your identity. The session may have expired.',
      );
      return;
    }

    // Phase 2 — setting-up (arc → ~80%)
    this.phase.set('setting-up');
    this.stepIndex.set(1);
    this.arcOffset.set(this.ARC_FULL * 0.2);
    this.cdr.markForCheck();

    await this.wait(900);

    // Phase 3 — welcoming (arc completes)
    this.phase.set('welcoming');
    this.stepIndex.set(2);
    this.arcOffset.set(0);
    this.cdr.markForCheck();

    await this.wait(650);

    // ── Navigate ─────────────────────────────────────────────────────────────
    // CRITICAL: Cancel the safety timeout BEFORE calling router.navigate().
    // withViewTransitions() keeps this component alive in the DOM during the
    // route animation, so the 15-second timer would otherwise fire mid-transition
    // and incorrectly show the error screen after a successful sign-in.
    if (this.timeoutHandle) {
      clearTimeout(this.timeoutHandle);
      this.timeoutHandle = null;
    }

    const isNewUserFlow = this.isNewUser();
    const completedOnboard = AuthIntentStorage.isOnboardingCompleted();

    if (isNewUserFlow && !completedOnboard) {
      // New user → onboarding questions in sign-up page
      AuthIntentStorage.setRegisterFlow(false);
      this.router.navigate(['/sign-up'], {
        replaceUrl: true,
        queryParams: { step: 'onboarding' },
      });
    } else {
      const destination = AuthIntentStorage.consumeReturnUrl();
      this.router.navigateByUrl(destination, { replaceUrl: true });
    }
  }

  private showError(message: string): void {
    if (this.timeoutHandle) {
      clearTimeout(this.timeoutHandle);
      this.timeoutHandle = null;
    }
    // Clean up stale flags so next sign-in starts fresh
    AuthIntentStorage.clearRegistrationIntent();
    this.keycloakService.clearInitError();

    this.errorDetail.set(message);
    this.phase.set('error');
    this.cdr.markForCheck();
  }

  retry():  void { this.router.navigate(['/sign-in'], { replaceUrl: true }); }
  goHome(): void { this.router.navigate(['/sign-in'], { replaceUrl: true }); }

  private wait(ms: number): Promise<void> {
    return new Promise(resolve => {
      this.progressHandle = setTimeout(resolve, ms);
    });
  }
}
