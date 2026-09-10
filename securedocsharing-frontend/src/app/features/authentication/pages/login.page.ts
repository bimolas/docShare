import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * LoginPageComponent: Authentication placeholder.
 * Will integrate Keycloak redirect flow in the next phase.
 */
@Component({
  selector: 'app-login-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="login-container">
      <div class="login-card">
        <h1 class="login-title">SecureDocSharing</h1>
        <p class="login-subtitle">Enterprise Document Management Platform</p>
        <button class="login-btn" [routerLink]="['/dashboard']">
          Sign in with Keycloak
        </button>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--color-bg);
    }

    .login-card {
      background: var(--color-surface);
      border: 1px solid var(--color-border);
      border-radius: 12px;
      padding: 2.5rem;
      width: 100%;
      max-width: 400px;
      text-align: center;
      box-shadow: 0 4px 24px rgba(0,0,0,0.06);
    }

    .login-title {
      font-size: 1.5rem;
      font-weight: 700;
      color: var(--color-accent);
      margin-bottom: 0.5rem;
    }

    .login-subtitle {
      color: var(--color-text-muted);
      font-size: 0.875rem;
      margin-bottom: 2rem;
    }

    .login-btn {
      width: 100%;
      padding: 0.75rem;
      background: var(--color-accent);
      color: white;
      border: none;
      border-radius: 8px;
      font-size: 0.875rem;
      font-weight: 600;
      cursor: pointer;
      transition: opacity 0.15s;
    }

    .login-btn:hover { opacity: 0.9; }
  `],
})
export class LoginPageComponent {}
