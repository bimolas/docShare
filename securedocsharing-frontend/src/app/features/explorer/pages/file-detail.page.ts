import { Component, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-file-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-placeholder">
      <h1>File Detail</h1>
      <p>Preview, metadata, SHA-256 integrity, version history, access control — coming soon.</p>
    </div>
  `,
  styles: [`.page-placeholder { padding: 2rem; } h1 { margin-bottom: 0.5rem; }`],
})
export class FileDetailPageComponent {}
