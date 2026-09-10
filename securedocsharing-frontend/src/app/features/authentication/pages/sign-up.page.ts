import {
  Component, ChangeDetectionStrategy, inject, signal, OnInit, OnDestroy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { KeycloakService } from '../../../core/auth/keycloak.service';
import { KeycloakApiService } from '../../../core/auth/keycloak-api.service';
import { AuthIntentStorage } from '../../../core/auth/auth-intent.storage';

type Step = 'credentials' | 'verify-email' | 'onboarding' | 'loading';

interface PasswordStrength {
  score: number; // 0-4
  label: string;
  color: string;
  checks: { label: string; passed: boolean }[];
}

@Component({
  selector: 'app-sign-up-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './sign-up.page.html',
  styleUrls: ['./sign-up.page.css'],
})
export class SignUpPageComponent implements OnInit, OnDestroy {
  private readonly keycloakService = inject(KeycloakService);
  private readonly keycloakApi = inject(KeycloakApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  async ngOnInit(): Promise<void> {
    if (!this.keycloakService.isInitialized()) {
      await this.keycloakService.init();
    }

    const requestedStep = this.route.snapshot.queryParams['step'];
    const isCompletedOnboarding = AuthIntentStorage.isOnboardingCompleted();

    if (this.keycloakService.isLoggedIn()) {
      if (requestedStep === 'onboarding' || (!isCompletedOnboarding && (AuthIntentStorage.isPendingOnboarding() || AuthIntentStorage.isRegisterFlow()))) {
        this.step.set('onboarding');
      } else {
        const target = this.getReturnUrl() || '/w/personal/dashboard';
        this.router.navigateByUrl(target);
        return;
      }
    } else if (requestedStep === 'onboarding') {
      this.step.set('onboarding');
    }

    const err = this.keycloakService.initError();
    if (err) {
      this.errorMessage.set(err);
    }
  }

  private getReturnUrl(): string | null {
    const returnUrl = this.route.snapshot.queryParams['returnUrl'];
    if (
      returnUrl &&
      typeof returnUrl === 'string' &&
      returnUrl.startsWith('/') &&
      !returnUrl.startsWith('/sign-in') &&
      !returnUrl.startsWith('/sign-up') &&
      !returnUrl.startsWith('/login')
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

  // ── Step state ──────────────────────────────────────────────────
  readonly step = signal<Step>('credentials');

  // ── Credentials form ────────────────────────────────────────────
  firstName = '';
  lastName = '';
  email = '';
  password = '';
  confirmPassword = '';
  showPassword = signal(false);
  showConfirm = signal(false);
  agreedToTerms = false;

  // ── Onboarding form ─────────────────────────────────────────────
  role = '';
  hearAboutUs = '';
  useCase = '';
  teamSize = '';

  // ── UI state ─────────────────────────────────────────────────────
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly isDuplicateEmail = signal(false);
  readonly resendCooldown = signal(0);
  private resendTimer: ReturnType<typeof setInterval> | null = null;

  // ── Password strength (getter, not computed, since fields are plain) ────────
  get passwordStrength(): PasswordStrength {
    const p = this.password;
    const checks = [
      { label: 'At least 8 characters', passed: p.length >= 8 },
      { label: 'Uppercase letter', passed: /[A-Z]/.test(p) },
      { label: 'Lowercase letter', passed: /[a-z]/.test(p) },
      { label: 'Number', passed: /\d/.test(p) },
      { label: 'Special character', passed: /[^A-Za-z0-9]/.test(p) },
    ];
    const score = checks.filter(c => c.passed).length;
    const map = [
      { label: '', color: 'transparent' },
      { label: 'Very weak', color: '#dc2626' },
      { label: 'Weak', color: '#fb923c' },
      { label: 'Fair', color: '#eab308' },
      { label: 'Strong', color: '#22c55e' },
      { label: 'Very strong', color: '#16a34a' },
    ];
    return { score, ...map[score], checks };
  }

  get canSubmitCredentials(): boolean {
    return (
      this.firstName.trim().length >= 1 &&
      this.lastName.trim().length >= 1 &&
      /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email) &&
      this.passwordStrength.score >= 3 &&
      this.password === this.confirmPassword &&
      this.agreedToTerms
    );
  }

  get canSubmitOnboarding(): boolean {
    return this.role !== '' && this.hearAboutUs !== '' && this.useCase !== '';
  }

  // ── Actions ──────────────────────────────────────────────────────
  submitCredentials(): void {
    if (!this.canSubmitCredentials) return;
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.isDuplicateEmail.set(false);

    // Set storage flags BEFORE the Keycloak redirect so the callback page
    // can detect a registration flow and route to onboarding instead of dashboard.
    this.saveReturnUrl();
    AuthIntentStorage.setRegisterFlow(true);
    AuthIntentStorage.setPendingOnboarding(true);

    this.keycloakApi.signUp({
      firstName: this.firstName,
      lastName: this.lastName,
      email: this.email,
      password: this.password
    }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.step.set('verify-email');
        this.startResendCooldown();
      },
      error: (err: Error) => {
        AuthIntentStorage.clearRegistrationIntent();
        this.isLoading.set(false);
        if (err.message.includes('already exists')) {
          this.isDuplicateEmail.set(true);
        } else {
          this.errorMessage.set(err.message);
        }
      }
    });
  }

  async submitOnboarding(): Promise<void> {
    if (!this.canSubmitOnboarding) return;
    this.isLoading.set(true);
    this.step.set('loading');

    AuthIntentStorage.setOnboardingCompleted(true);
    AuthIntentStorage.setOnboardingData({
      role: this.role,
      hearAboutUs: this.hearAboutUs,
      useCase: this.useCase,
      teamSize: this.teamSize,
    });

    setTimeout(() => {
      const target = AuthIntentStorage.consumeReturnUrl(
        this.getReturnUrl() || '/w/personal/dashboard'
      );
      this.router.navigateByUrl(target);
    }, 1200);
  }

  resendEmail(): void {
    if (this.resendCooldown() > 0) return;
    this.startResendCooldown();
    this.keycloakApi.sendVerificationEmail(this.email).subscribe({
      error: () => {
        // Silently handle errors
      }
    });
  }

  continueAfterVerification(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.keycloakApi.signIn(this.email, this.password).subscribe({
      next: (tokens) => {
        this.isLoading.set(false);
        this.keycloakService.setTokensFromRopc(tokens);
        this.step.set('onboarding');
      },
      error: (err: Error) => {
        this.isLoading.set(false);
        // If sign in fails here, it's likely because they haven't verified their email yet.
        this.errorMessage.set('Please verify your email address before continuing. Check your inbox.');
      }
    });
  }

  async signUpWithGoogle(): Promise<void> {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    // Set storage flags before Keycloak/Google redirect so callback routes to onboarding.
    this.saveReturnUrl();
    AuthIntentStorage.setRegisterFlow(true);
    AuthIntentStorage.setPendingOnboarding(true);
    try {
      await this.keycloakService.register({
        idpHint: 'google',
        redirectUri: this.getCallbackUri(),
      });
    } catch {
      AuthIntentStorage.clearRegistrationIntent();
      this.errorMessage.set('Unable to reach the authentication server. Please try again.');
      this.isLoading.set(false);
    }
  }

  private startResendCooldown(): void {
    this.resendCooldown.set(60);
    this.resendTimer = setInterval(() => {
      const v = this.resendCooldown() - 1;
      this.resendCooldown.set(v);
      if (v <= 0 && this.resendTimer) {
        clearInterval(this.resendTimer);
        this.resendTimer = null;
      }
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.resendTimer) clearInterval(this.resendTimer);
  }

  // ── Template helpers ─────────────────────────────────────────────
  get strengthBarWidth(): number {
    return (this.passwordStrength.score / 5) * 100;
  }

  readonly roles = [
    { value: 'developer', label: '👨‍💻 Developer / Engineer' },
    { value: 'manager', label: '📋 Manager / Team Lead' },
    { value: 'designer', label: '🎨 Designer / Creative' },
    { value: 'legal', label: '⚖️ Legal / Compliance' },
    { value: 'finance', label: '💼 Finance / Accounting' },
    { value: 'other', label: '🏷️ Other' },
  ];

  readonly hearAboutOptions = [
    { value: 'google', label: 'Google Search' },
    { value: 'twitter', label: 'Twitter / X' },
    { value: 'linkedin', label: 'LinkedIn' },
    { value: 'colleague', label: 'Colleague / Friend' },
    { value: 'blog', label: 'Blog / Article' },
    { value: 'producthunt', label: 'Product Hunt' },
    { value: 'other', label: 'Other' },
  ];

  readonly useCaseOptions = [
    { value: 'personal', label: 'Personal file storage' },
    { value: 'team', label: 'Team collaboration' },
    { value: 'client', label: 'Client document sharing' },
    { value: 'legal', label: 'Legal document management' },
    { value: 'compliance', label: 'Compliance & auditing' },
    { value: 'other', label: 'Other' },
  ];

  readonly teamSizeOptions = [
    { value: '1', label: 'Just me' },
    { value: '2-10', label: '2–10 people' },
    { value: '11-50', label: '11–50 people' },
    { value: '51-200', label: '51–200 people' },
    { value: '201+', label: '201+ people' },
  ];
}

