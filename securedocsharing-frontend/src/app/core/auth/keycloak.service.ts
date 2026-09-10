import { Injectable, signal } from '@angular/core';
import Keycloak from 'keycloak-js';
import { Observable, from, of } from 'rxjs';
import { catchError, map, shareReplay, tap } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { AuthIntentStorage } from './auth-intent.storage';

export interface KeycloakLoginOptions {
  redirectUri?: string;
  idpHint?: string;
  scope?: string;
  prompt?: 'none' | 'login' | 'consent';
  loginHint?: string;
  action?: string;
  locale?: string;
  maxAge?: number;
  uiLocales?: string;
}

/**
 * Error messages that are expected during normal unauthenticated check-sso
 * and should NOT be surfaced to the user as an error.
 */
const BENIGN_OAUTH_ERRORS = new Set([
  'Session not found',
  'Login required',
]);

@Injectable({ providedIn: 'root' })
export class KeycloakService {
  private keycloakInstance: Keycloak | null = null;

  // Reactive state signals
  private readonly _isInitialized = signal<boolean>(false);
  private readonly _authenticated = signal<boolean>(false);
  private readonly _token = signal<string | null>(null);
  private readonly _initError = signal<string | null>(null);

  readonly isInitialized = this._isInitialized.asReadonly();
  readonly authenticated = this._authenticated.asReadonly();
  readonly token = this._token.asReadonly();
  readonly initError = this._initError.asReadonly();

  // Concurrency lock for simultaneous 401 token refresh requests
  private isRefreshing = false;
  private refreshObservable: Observable<boolean> | null = null;

  // Concurrency lock for init execution
  private initPromise: Promise<boolean> | null = null;

  /**
   * Initializes the Keycloak OIDC client instance.
   * KeycloakService is the sole owner of the Keycloak lifecycle.
   * Access tokens and refresh tokens are maintained strictly in-memory (no localStorage).
   */
  async init(): Promise<boolean> {
    if (this._isInitialized()) {
      return this._authenticated();
    }
    if (this.initPromise) {
      return this.initPromise;
    }
    this.initPromise = this.doInit();
    return this.initPromise;
  }

  private async doInit(): Promise<boolean> {
    console.log('[AUTH] KeycloakService.init() START', Date.now(), typeof window !== 'undefined' ? window.location.href : '');

    // ── Detect OAuth callback vs. normal page load ──────────────────────────
    // When Keycloak redirects back after login/Google SSO, the URL contains
    // ?code=...&state=... These parameters tell us we need to exchange the
    // authorization code for tokens (PKCE flow).
    //
    // onLoad: 'check-sso' silently checks for an existing Keycloak session and
    // also processes a code/state pair if found in the URL. However in some
    // environments (especially after a full-page Google redirect), the silent
    // SSO iframe can interfere with the code exchange.
    //
    // Strategy:
    //   • URL has ?code= → we are on the callback → use 'login-required'
    //     so Keycloak.js does a direct code exchange without spawning the
    //     silent-check-sso iframe.
    //   • No code in URL  → normal page load → use 'check-sso' with the
    //     silent redirect so existing sessions are detected without a redirect.
    const hasOAuthCode = typeof window !== 'undefined' &&
      new URLSearchParams(window.location.search).has('code');

    // Clear any stale PKCE remnants from previous failed exchanges BEFORE
    // creating a new Keycloak instance so they don't pollute the new flow.
    // NOTE: Do NOT call clearStaleKeycloakSession() here!
    // Keycloak.js relies on kc-pkce-<clientId> stored in sessionStorage to complete
    // the authorization code exchange. Clearing it before init() causes PKCE token exchange to fail.

    // Check for OAuth *error* parameters returned by Keycloak/IdP.
    if (typeof window !== 'undefined') {
      const urlParams = new URLSearchParams(window.location.search);
      const errorCode = urlParams.get('error');
      const errorDesc = urlParams.get('error_description');
      let hashErrorCode: string | null = null;
      let hashErrorDesc: string | null = null;
      if (window.location.hash) {
        const hashContent = window.location.hash.startsWith('#') ? window.location.hash.substring(1) : window.location.hash;
        if (hashContent.includes('error=')) {
          const hashParams = new URLSearchParams(hashContent);
          hashErrorCode = hashParams.get('error');
          hashErrorDesc = hashParams.get('error_description');
        }
      }
      const finalError = errorDesc || errorCode || hashErrorDesc || hashErrorCode;
      if (finalError) {
        const decodedError = decodeURIComponent(finalError);
        if (!BENIGN_OAUTH_ERRORS.has(decodedError)) {
          console.warn('[AUTH] Detected OAuth error parameter in URL:', decodedError);
          this._initError.set(decodedError);
        } else {
          console.log('[AUTH] Suppressing benign OAuth error parameter:', decodedError);
        }
      }
    }

    this.keycloakInstance = new Keycloak({
      url: environment.keycloak.url,
      realm: environment.keycloak.realm,
      clientId: environment.keycloak.clientId,
    });

    try {
      const silentSsoUri = typeof window !== 'undefined'
        ? window.location.origin + '/silent-check-sso.html'
        : undefined;

      // Choose init strategy based on whether this is a callback or normal load.
      //
      // IMPORTANT: When the URL contains ?code= (OAuth callback), do NOT use
      // onLoad:'check-sso'. The check-sso path spawns a silent iframe that runs
      // in a separate browsing context with no access to the parent tab's
      // sessionStorage where the PKCE code-verifier is stored. This causes
      // keycloak.js to fail the code exchange with a PKCE error every time.
      //
      // Omitting 'onLoad' tells keycloak.js to process the code/state params
      // directly from the URL without any iframe, which is the correct path.
      const initOptions: Keycloak.KeycloakInitOptions = hasOAuthCode
        ? {
            // No onLoad — direct code exchange from URL params (no silent iframe).
            checkLoginIframe: false,
            pkceMethod: 'S256',
          }
        : {
            onLoad: 'check-sso',
            silentCheckSsoRedirectUri: silentSsoUri,
            checkLoginIframe: false,
            pkceMethod: 'S256',
          };


      console.log('[AUTH] calling keycloakInstance.init()...', Date.now(),
        typeof window !== 'undefined' ? window.location.href : '',
        'hasOAuthCode:', hasOAuthCode);

      const authenticated = await this.keycloakInstance.init(initOptions);
      console.log('[AUTH] keycloakInstance.init RESULT:', authenticated, Date.now(),
        typeof window !== 'undefined' ? window.location.href : '');

      if (hasOAuthCode && !authenticated) {
        console.warn('[AUTH] Code exchange returned unauthenticated. Attempting fallback check-sso...');
        this.clearOAuthParameters();
        this.clearStaleKeycloakSession();

        // Create fresh Keycloak instance for fallback check-sso
        this.keycloakInstance = new Keycloak({
          url: environment.keycloak.url,
          realm: environment.keycloak.realm,
          clientId: environment.keycloak.clientId,
        });

        try {
          const fallbackAuth = await this.keycloakInstance.init({
            onLoad: 'check-sso',
            silentCheckSsoRedirectUri: silentSsoUri,
            checkLoginIframe: false,
            pkceMethod: 'S256',
          });

          if (fallbackAuth && this.keycloakInstance.token) {
            // Genuine session recovery — we have both an SSO session AND a token.
            console.log('[AUTH] Fallback check-sso succeeded with token after unauthenticated code response');
            this._authenticated.set(true);
            this._token.set(this.keycloakInstance.token);
            this._initError.set(null);
            this.keycloakInstance.onTokenExpired = () => {
              console.log('[AUTH] keycloakInstance.onTokenExpired', Date.now());
              this.refreshToken().subscribe();
            };
            this._isInitialized.set(true);
            return true;
          } else if (fallbackAuth && !this.keycloakInstance.token) {
            // Phantom auth guard: SSO cookie exists but code exchange already consumed
            // the grant — no access token available. Force a full logout to clear the
            // server-side session so the user can sign in cleanly on the next attempt.
            console.warn('[AUTH] Phantom auth detected: SSO session without access token. Forcing logout.');
            this._initError.set('Your previous session expired. Please sign in again.');
            this._isInitialized.set(true);
            this._authenticated.set(false);
            this._token.set(null);
            // Fire-and-forget: redirect to /sign-in after clearing the KC session.
            this.keycloakInstance.logout({
              redirectUri: typeof window !== 'undefined' ? window.location.origin + '/sign-in' : undefined,
            }).catch(() => {});
            return false;
          }
        } catch (fallbackErr) {
          console.warn('[AUTH] Fallback check-sso failed:', fallbackErr);
        }

        this._isInitialized.set(true);
        this._authenticated.set(false);
        this._initError.set('Authentication session expired. Please sign in again.');
        return false;
      }

      this._authenticated.set(authenticated);
      if (authenticated && this.keycloakInstance.token) {
        this._token.set(this.keycloakInstance.token);
        this._initError.set(null);
      } else {
        this._token.set(null);
      }

      // Strip OIDC callback parameters from the URL bar without navigation.
      this.clearOAuthParameters();
      this._isInitialized.set(true);

      this.keycloakInstance.onTokenExpired = () => {
        console.log('[AUTH] keycloakInstance.onTokenExpired', Date.now());
        this.refreshToken().subscribe();
      };

      return authenticated;
    } catch (error) {
      const errStr = error instanceof Error
        ? error.message
        : (typeof error === 'string' ? error : 'Keycloak initialization failed.');
      console.warn('[AUTH] Keycloak initialization failed:', error, Date.now());

      if (hasOAuthCode) {
        console.warn('[AUTH] Code exchange threw exception (e.g. PKCE verifier missing in new tab). Attempting fallback check-sso...');
        this.clearOAuthParameters();
        this.clearStaleKeycloakSession();

        const silentSsoUri = typeof window !== 'undefined'
          ? window.location.origin + '/silent-check-sso.html'
          : undefined;

        this.keycloakInstance = new Keycloak({
          url: environment.keycloak.url,
          realm: environment.keycloak.realm,
          clientId: environment.keycloak.clientId,
        });

        try {
          const fallbackAuth = await this.keycloakInstance.init({
            onLoad: 'check-sso',
            silentCheckSsoRedirectUri: silentSsoUri,
            checkLoginIframe: false,
            pkceMethod: 'S256',
          });

          if (fallbackAuth && this.keycloakInstance.token) {
            // Genuine recovery with a valid access token.
            console.log('[AUTH] Fallback check-sso succeeded with token after code exchange exception');
            this._authenticated.set(true);
            this._token.set(this.keycloakInstance.token);
            this._initError.set(null);
            this.keycloakInstance.onTokenExpired = () => {
              console.log('[AUTH] keycloakInstance.onTokenExpired', Date.now());
              this.refreshToken().subscribe();
            };
            this._isInitialized.set(true);
            return true;
          } else if (fallbackAuth && !this.keycloakInstance.token) {
            // Phantom auth: SSO cookie present but no token from exception path.
            console.warn('[AUTH] Phantom auth (exception path): SSO session without token. Forcing logout.');
            this._initError.set('Your previous session expired. Please sign in again.');
            this._isInitialized.set(true);
            this._authenticated.set(false);
            this._token.set(null);
            this.keycloakInstance.logout({
              redirectUri: typeof window !== 'undefined' ? window.location.origin + '/sign-in' : undefined,
            }).catch(() => {});
            return false;
          }
        } catch (fallbackErr) {
          console.warn('[AUTH] Fallback check-sso error after exception:', fallbackErr);
        }
      }

      if (!this._initError()) {
        this._initError.set(errStr);
      }
      this.clearStaleKeycloakSession();
      this.clearOAuthParameters();
      this._isInitialized.set(true);
      this._authenticated.set(false);
      this._token.set(null);
      return false;
    } finally {
      this.initPromise = null;
    }
  }

  /**
   * Resets internal token state without triggering redirect.
   */
  public clearTokensAndState(): void {
    this._authenticated.set(false);
    this._token.set(null);
  }

  /**
   * Clears any initialization error stored in state.
   */
  public clearInitError(): void {
    this._initError.set(null);
  }

  /**
   * Accepts tokens from an ROPC (Resource Owner Password Credentials) grant
   * and updates the service state so guards, interceptors, and the AuthStore
   * all see an authenticated session — without an OIDC redirect.
   *
   * Called by SignInPageComponent after a direct email/password sign-in.
   */
  public setTokensFromRopc(tokens: { access_token: string; refresh_token?: string }): void {
    this._token.set(tokens.access_token);
    this._authenticated.set(true);
    this._isInitialized.set(true);
    this._initError.set(null);

    // Ensure a Keycloak instance exists so updateToken() / refreshToken() work.
    // On a fresh page load where init() was never called (rare but possible if
    // the sign-in page is the landing page), create a minimal shell instance.
    if (!this.keycloakInstance) {
      this.keycloakInstance = new Keycloak({
        url: environment.keycloak.url,
        realm: environment.keycloak.realm,
        clientId: environment.keycloak.clientId,
      });
    }
    this.keycloakInstance.token = tokens.access_token;
    if (tokens.refresh_token) {
      this.keycloakInstance.refreshToken = tokens.refresh_token;
    }
    // Wire up automatic token refresh on expiry.
    this.keycloakInstance.onTokenExpired = () => {
      console.log('[AUTH] keycloakInstance.onTokenExpired (ROPC)', Date.now());
      this.refreshToken().subscribe();
    };
    console.log('[AUTH] KeycloakService.setTokensFromRopc(): tokens accepted from ROPC grant');
  }


  /**
   * Removes stale Keycloak PKCE / callback session entries left by a failed or
   * interrupted authorization-code exchange. Keycloak.js stores the PKCE
   * code-verifier and state under keys like `kc-callback-<clientId>` and
   * `kc-pkce-<clientId>`. If a code exchange fails (tab switch, network error,
   * expired code), these entries survive in sessionStorage and cause the next
   * `check-sso` call to appear authenticated with no valid token (phantom auth).
   */
  private clearStaleKeycloakSession(): void {
    if (typeof window === 'undefined') return;
    const keysToRemove: string[] = [];
    for (let i = 0; i < sessionStorage.length; i++) {
      const key = sessionStorage.key(i);
      if (key && (
        key.startsWith('kc-callback-') ||
        key.startsWith('kc-pkce-') ||
        key.startsWith('kc-state-') ||
        key === 'kc_idpHint'
      )) {
        keysToRemove.push(key);
      }
    }
    keysToRemove.forEach(k => sessionStorage.removeItem(k));
    console.log('[AUTH] clearStaleKeycloakSession: removed', keysToRemove.length, 'stale PKCE entries');
  }

  /**
   * Strips OAuth redirect query/fragment parameters (code, state, session_state, iss, error, etc.) from the URL bar.
   */
  private clearOAuthParameters(): void {
    console.log('[AUTH] clearOAuthParameters() RUNNING', Date.now(), typeof window !== 'undefined' ? window.location.href : '');
    if (typeof window === 'undefined' || !window.history?.replaceState) {
      return;
    }

    const url = new URL(window.location.href);
    const oauthParams = ['code', 'state', 'session_state', 'iss', 'execution', 'client_id', 'error', 'error_description', 'error_reason'];
    let modified = false;

    oauthParams.forEach((param) => {
      if (url.searchParams.has(param)) {
        url.searchParams.delete(param);
        modified = true;
      }
    });

    let newHash = url.hash;
    if (newHash) {
      const hashContent = newHash.startsWith('#') ? newHash.substring(1) : newHash;
      if (hashContent.includes('code=') || hashContent.includes('state=') || hashContent.includes('error=')) {
        const hashParams = new URLSearchParams(hashContent);
        oauthParams.forEach((param) => hashParams.delete(param));
        const remainingHash = hashParams.toString();
        newHash = remainingHash ? `#${remainingHash}` : '';
        modified = true;
      }
    }

    if (modified) {
      const cleanUrl = url.pathname + (url.searchParams.toString() ? `?${url.searchParams.toString()}` : '') + newHash;
      console.log('[AUTH] clearOAuthParameters() REPLACING STATE TO:', cleanUrl, Date.now());
      window.history.replaceState(window.history.state, '', cleanUrl);
    }
  }

  /**
   * Helper to strip OAuth query/fragment parameters from any URL string.
   */
  public stripOAuthFromUrl(urlString: string): string {
    try {
      const url = new URL(urlString, typeof window !== 'undefined' ? window.location.origin : 'http://localhost');
      const oauthParams = ['code', 'state', 'session_state', 'iss', 'execution', 'client_id', 'error', 'error_description', 'error_reason'];
      oauthParams.forEach((param) => url.searchParams.delete(param));
      if (url.hash) {
        const hashContent = url.hash.startsWith('#') ? url.hash.substring(1) : url.hash;
        if (hashContent.includes('code=') || hashContent.includes('state=') || hashContent.includes('error=')) {
          const hashParams = new URLSearchParams(hashContent);
          oauthParams.forEach((param) => hashParams.delete(param));
          const remainingHash = hashParams.toString();
          url.hash = remainingHash ? `#${remainingHash}` : '';
        }
      }
      return url.toString();
    } catch {
      return urlString;
    }
  }

  /**
   * Initiates the Authorization Code + PKCE login flow.
   *
   * Accepts either:
   * - A plain redirect URI string  → login({ redirectUri })
   * - A KeycloakLoginOptions object → login({ redirectUri, idpHint, ... })
   *
   * The redirect URI is automatically stripped of any leftover OAuth parameters
   * before being sent to Keycloak so the post-login redirect lands cleanly.
   */
  async login(options?: KeycloakLoginOptions | string): Promise<void> {
    const opts: KeycloakLoginOptions =
      typeof options === 'string' ? { redirectUri: options } : (options ?? {});
    console.log('[AUTH] KeycloakService.login() CALLED', Date.now(),
      typeof window !== 'undefined' ? window.location.href : '', 'opts:', opts);

    if (!this._isInitialized() || !this.keycloakInstance) {
      await this.init();
    }

    if (!this.keycloakInstance) {
      throw new Error('Keycloak client is not initialized.');
    }

    // Build a clean redirect URI — strip any stale OAuth params that might
    // have survived from a previous auth cycle.
    const rawTarget = opts.redirectUri ||
      (typeof window !== 'undefined' ? window.location.href : '');
    const cleanRedirectUri = this.stripOAuthFromUrl(rawTarget);
    console.log('[AUTH] KeycloakService.login() → redirectUri:', cleanRedirectUri, Date.now());

    const keycloakOpts: Keycloak.KeycloakLoginOptions = {
      ...opts,
      redirectUri: cleanRedirectUri,
    };

    await this.keycloakInstance.login(keycloakOpts);
  }

  /**
   * Initiates OIDC registration flow via Keycloak's native registration endpoint.
   * Supports prefilled email/loginHint, idpHint, and custom redirectUri.
   */
  async register(options?: KeycloakLoginOptions | string): Promise<void> {
    const opts: KeycloakLoginOptions = typeof options === 'string' ? { redirectUri: options } : (options || {});
    console.log('[AUTH] KeycloakService.register() CALLED!', Date.now(), typeof window !== 'undefined' ? window.location.href : '', 'opts input:', opts);

    if (!this._isInitialized() || !this.keycloakInstance) {
      await this.init();
    }

    if (!this.keycloakInstance) {
      throw new Error('Keycloak client is not initialized.');
    }

    const targetUri = opts.redirectUri || (typeof window !== 'undefined' ? window.location.href : '');
    const cleanRedirectUri = this.stripOAuthFromUrl(targetUri);
    console.log('[AUTH] KeycloakService.register() INITIATING REGISTRATION REDIRECT with cleanRedirectUri:', cleanRedirectUri, Date.now());

    const keycloakRegisterOpts: any = {
      ...opts,
      redirectUri: cleanRedirectUri,
      action: 'register',
    };

    AuthIntentStorage.setRegisterFlow(true);

    await this.keycloakInstance.register(keycloakRegisterOpts);
  }

  /**
   * Initiates OIDC logout flow.
   */
  async logout(redirectUri?: string): Promise<void> {
    this.clearTokensAndState();
    if (this.keycloakInstance) {
      await this.keycloakInstance.logout({
        redirectUri: redirectUri || (typeof window !== 'undefined' ? window.location.origin + '/sign-in' : undefined),
      });
    }
  }

  /**
   * Returns current access token from memory.
   */
  getToken(): string | null {
    if (this.keycloakInstance?.token) {
      this._token.set(this.keycloakInstance.token);
      return this.keycloakInstance.token;
    }
    return this._token();
  }

  /**
   * Checks if user is authenticated.
   */
  isLoggedIn(): boolean {
    return this._authenticated();
  }

  /**
   * Checks if current token is expired.
   */
  isTokenExpired(minValidity = 30): boolean {
    if (!this.keycloakInstance) {
      return true;
    }
    return this.keycloakInstance.isTokenExpired(minValidity);
  }

  /**
   * Refreshes the OIDC access token.
   * Coalesces concurrent 401 refresh requests into a single in-flight update.
   */
  refreshToken(minValidity = 30): Observable<boolean> {
    console.log('[AUTH] KeycloakService.refreshToken() CALLED', Date.now(), typeof window !== 'undefined' ? window.location.href : '');
    if (!this.keycloakInstance || !this._authenticated()) {
      console.log('[AUTH] KeycloakService.refreshToken() SKIPPED: not authenticated or no instance', Date.now());
      return of(false);
    }

    // Return existing in-flight refresh observable if already refreshing
    if (this.isRefreshing && this.refreshObservable) {
      console.log('[AUTH] KeycloakService.refreshToken() REUSING IN-FLIGHT REFRESH', Date.now());
      return this.refreshObservable;
    }

    this.isRefreshing = true;

    this.refreshObservable = from(this.keycloakInstance.updateToken(minValidity)).pipe(
      tap((refreshed) => {
        console.log('[AUTH] KeycloakService.refreshToken() RESULT refreshed =', refreshed, Date.now());
        if (refreshed && this.keycloakInstance?.token) {
          this._token.set(this.keycloakInstance.token);
        }
        this.isRefreshing = false;
        this.refreshObservable = null;
      }),
      map(() => true),
      catchError((err) => {
        console.warn('[AUTH] Failed to refresh Keycloak token silently:', err, Date.now());
        this.isRefreshing = false;
        this.refreshObservable = null;
        this.clearTokensAndState();
        return of(false);
      }),
      shareReplay(1)
    );

    return this.refreshObservable;
  }
}
