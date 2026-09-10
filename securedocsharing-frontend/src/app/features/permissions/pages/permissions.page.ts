import { Component, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-permissions-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-placeholder">
      <h1>Permissions Hub</h1>
      <p>Centralized table of all users with access — filters, inline update, bulk revoke — coming soon.</p>
    </div>
  `,
  styles: [`.page-placeholder { padding: 2rem; } h1 { margin-bottom: 0.5rem; }`],
})
export class PermissionsPageComponent {}
