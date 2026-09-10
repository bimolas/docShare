import { Component, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-folder-detail-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-placeholder">
      <h1>Folder Detail</h1>
      <p>Metadata summary, child items, folder activity feed, access panel — coming soon.</p>
    </div>
  `,
  styles: [`.page-placeholder { padding: 2rem; } h1 { margin-bottom: 0.5rem; }`],
})
export class FolderDetailPageComponent {}
