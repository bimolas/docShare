import { Component, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-profile-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-placeholder">
      <h1>Profile</h1>
      <p>User profile, avatar, password change, MFA settings — coming soon.</p>
    </div>
  `,
  styles: [`.page-placeholder { padding: 2rem; } h1 { margin-bottom: 0.5rem; }`],
})
export class ProfilePageComponent {}
