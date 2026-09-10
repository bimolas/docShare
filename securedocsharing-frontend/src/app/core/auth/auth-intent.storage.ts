/**
 * AuthIntentStorage
 *
 * Manages non-sensitive registration, onboarding, and navigation intent state.
 * Uses localStorage so that intent persists across browser tabs (e.g. when an
 * email verification link opens in a new tab).
 *
 * NOTE: Security-sensitive items (tokens, PKCE verifiers, client secrets) MUST
 * NOT be stored here. OAuth/PKCE transaction state remains in Keycloak-managed
 * sessionStorage, and tokens remain strictly in-memory in KeycloakService.
 */
export class AuthIntentStorage {
  private static readonly KEY_REGISTER_FLOW = 'kc_register_flow';
  private static readonly KEY_PENDING_ONBOARDING = 'pending_onboarding';
  private static readonly KEY_ONBOARDING_COMPLETED = 'onboarding_completed';
  private static readonly KEY_ONBOARDING_DATA = 'onboarding_data';
  private static readonly KEY_RETURN_URL = 'auth_return_url';

  // ── Registration / Onboarding Intent ──────────────────────────────────

  static setRegisterFlow(isRegister: boolean): void {
    if (typeof window === 'undefined') return;
    if (isRegister) {
      localStorage.setItem(this.KEY_REGISTER_FLOW, 'true');
    } else {
      localStorage.removeItem(this.KEY_REGISTER_FLOW);
    }
  }

  static isRegisterFlow(): boolean {
    if (typeof window === 'undefined') return false;
    return localStorage.getItem(this.KEY_REGISTER_FLOW) === 'true';
  }

  static setPendingOnboarding(pending: boolean): void {
    if (typeof window === 'undefined') return;
    if (pending) {
      localStorage.setItem(this.KEY_PENDING_ONBOARDING, 'true');
    } else {
      localStorage.removeItem(this.KEY_PENDING_ONBOARDING);
    }
  }

  static isPendingOnboarding(): boolean {
    if (typeof window === 'undefined') return false;
    return localStorage.getItem(this.KEY_PENDING_ONBOARDING) === 'true';
  }

  static setOnboardingCompleted(completed: boolean): void {
    if (typeof window === 'undefined') return;
    if (completed) {
      localStorage.setItem(this.KEY_ONBOARDING_COMPLETED, 'true');
      localStorage.removeItem(this.KEY_PENDING_ONBOARDING);
      localStorage.removeItem(this.KEY_REGISTER_FLOW);
    } else {
      localStorage.removeItem(this.KEY_ONBOARDING_COMPLETED);
    }
  }

  static isOnboardingCompleted(): boolean {
    if (typeof window === 'undefined') return false;
    return localStorage.getItem(this.KEY_ONBOARDING_COMPLETED) === 'true';
  }

  static setOnboardingData(data: Record<string, unknown>): void {
    if (typeof window === 'undefined') return;
    localStorage.setItem(this.KEY_ONBOARDING_DATA, JSON.stringify(data));
  }

  static getOnboardingData(): Record<string, unknown> | null {
    if (typeof window === 'undefined') return null;
    const data = localStorage.getItem(this.KEY_ONBOARDING_DATA);
    if (!data) return null;
    try {
      return JSON.parse(data);
    } catch {
      return null;
    }
  }

  // ── Return URL ────────────────────────────────────────────────────────

  static setReturnUrl(url: string): void {
    if (typeof window === 'undefined') return;
    if (
      url &&
      url.startsWith('/') &&
      !url.startsWith('/sign-in') &&
      !url.startsWith('/sign-up') &&
      !url.startsWith('/login') &&
      !url.startsWith('/auth/callback')
    ) {
      localStorage.setItem(this.KEY_RETURN_URL, url);
    }
  }

  static consumeReturnUrl(defaultUrl = '/w/personal/dashboard'): string {
    if (typeof window === 'undefined') return defaultUrl;
    const stored = localStorage.getItem(this.KEY_RETURN_URL);
    if (stored) {
      localStorage.removeItem(this.KEY_RETURN_URL);
      if (
        stored.startsWith('/') &&
        !stored.startsWith('/sign-in') &&
        !stored.startsWith('/sign-up') &&
        !stored.startsWith('/login') &&
        !stored.startsWith('/auth/callback')
      ) {
        return stored;
      }
    }
    return defaultUrl;
  }

  // ── Clear All Intent ──────────────────────────────────────────────────

  static clearRegistrationIntent(): void {
    if (typeof window === 'undefined') return;
    localStorage.removeItem(this.KEY_REGISTER_FLOW);
    localStorage.removeItem(this.KEY_PENDING_ONBOARDING);
  }
}
