import { Component, ChangeDetectionStrategy, input, output, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FolderPeekResponseDto } from '../../../core/models/folder.model';

@Component({
  selector: 'app-item-detail-header',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="drawer-header">
      <div class="header-top-row">
        <div class="header-main-info">
          <div class="header-hero-icon" [ngClass]="heroIconClass()">
            @if (item()?.type === 'folder') {
              <svg viewBox="0 0 24 24" fill="currentColor"><path d="M20 6h-8l-2-2H4C2.9 4 2 4.9 2 6v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2z"/></svg>
            } @else {
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
            }
          </div>
          <div class="header-title-meta">
            <div class="header-space-badge" [class.org-space]="isOrgSpace()">
              @if (isOrgSpace()) {
                <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="2" width="10" height="12" rx="1"/><line x1="6" y1="5" x2="10" y2="5"/><line x1="6" y1="8" x2="10" y2="8"/><line x1="6" y1="11" x2="10" y2="11"/></svg>
                <span>Organization Space</span>
              } @else {
                <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"/><path d="M2 14c0-3.3 2.7-6 6-6s6 2.7 6 6"/></svg>
                <span>Personal Space</span>
              }
              <span class="badge-dot">•</span>
              <span>{{ item()?.type === 'folder' ? 'Folder' : fileCategoryLabel() }}</span>
            </div>
            <h3 class="header-item-name" [title]="itemName()">{{ itemName() }}</h3>
          </div>
        </div>
        <button class="close-btn" (click)="closed.emit()" title="Close (Esc)" aria-label="Close detail drawer">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>

      <!-- Location & Meta Breadcrumb Row -->
      <div class="header-meta-bar">
        <div class="location-breadcrumb">
          <svg class="loc-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M14 4.5V13a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1h5.5L11 4.5z"/><polyline points="14 4.5 11 4.5 11 2"/></svg>
          <div class="crumb-list">
            @for (crumb of locationBreadcrumbs(); track $index) {
              @if (!$first) { <span class="crumb-sep">/</span> }
              <span class="crumb-item" [title]="crumb">{{ crumb }}</span>
            }
          </div>
        </div>
        <div class="size-meta-badge">
          @if (item()?.type === 'folder') {
            <span>{{ folderPeek()?.fileCount ?? 0 }} items • {{ formattedSize() }}</span>
          } @else {
            <span>{{ formattedSize() }}</span>
          }
        </div>
      </div>
    </div>
  `,
  styles: [`
    .drawer-header {
      display: flex;
      flex-direction: column;
      gap: 0.875rem;
      padding: 1.25rem 1.25rem 1rem;
      background: linear-gradient(135deg, #fff7f3 0%, #ffffff 100%);
      border-bottom: 1px solid #fde8db;
    }
    .header-top-row {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 0.75rem;
    }
    .header-main-info {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      flex: 1;
      min-width: 0;
    }
    .header-hero-icon {
      width: 2.75rem; height: 2.75rem;
      border-radius: 0.75rem;
      display: flex; align-items: center; justify-content: center;
      flex-shrink: 0;
      box-shadow: 0 2px 4px rgba(0,0,0,0.04);
    }
    .header-hero-icon svg { width: 1.5rem; height: 1.5rem; }
    .hero-folder { background: rgba(255,85,0,0.12); color: #ff5500; border: 1px solid rgba(255,85,0,0.2); }
    .hero-doc    { background: rgba(2,132,199,0.1);  color: #0284c7; border: 1px solid rgba(2,132,199,0.2); }

    .header-title-meta {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      min-width: 0;
      flex: 1;
    }
    .header-space-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.1875rem 0.5rem;
      border-radius: 9999px;
      background: #fff7f3;
      color: #ea580c;
      border: 1px solid #fed7aa;
      font-size: 0.6875rem;
      font-weight: 600;
      width: fit-content;
    }
    .header-space-badge.org-space {
      background: #f0fdf4;
      color: #166534;
      border-color: #bbf7d0;
    }
    .header-space-badge svg { width: 0.75rem; height: 0.75rem; }
    .badge-dot { font-size: 0.5rem; opacity: 0.6; }

    .header-item-name {
      font-size: 1rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      line-height: 1.3;
    }
    .close-btn {
      background: none; border: none; cursor: pointer;
      color: #94a3b8; padding: 0.375rem;
      border-radius: 0.375rem; flex-shrink: 0;
      transition: background 0.15s, color 0.15s;
    }
    .close-btn:hover { background: #f1f5f9; color: #0f172a; }
    .close-btn svg { width: 1.25rem; height: 1.25rem; }

    .header-meta-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
      padding: 0.5rem 0.75rem;
      background: #ffffff;
      border: 1px solid #f1f5f9;
      border-radius: 0.5rem;
    }
    .location-breadcrumb {
      display: flex;
      align-items: center;
      gap: 0.375rem;
      min-width: 0;
      flex: 1;
    }
    .loc-icon { width: 0.875rem; height: 0.875rem; color: #ff5500; flex-shrink: 0; }
    .crumb-list {
      display: flex;
      align-items: center;
      gap: 0.25rem;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 0.71875rem;
      color: #64748b;
      font-weight: 500;
    }
    .crumb-sep { color: #cbd5e1; font-size: 0.6875rem; }
    .crumb-item { overflow: hidden; text-overflow: ellipsis; }
    .crumb-item:last-child { font-weight: 600; color: #0f172a; }

    .size-meta-badge {
      font-size: 0.6875rem;
      font-weight: 600;
      color: #475569;
      background: #f8fafc;
      padding: 0.125rem 0.375rem;
      border-radius: 0.25rem;
      border: 1px solid #e2e8f0;
      flex-shrink: 0;
    }
  `]
})
export class ItemDetailHeaderComponent {
  item = input<{ type: 'folder' | 'document'; data: any; tab?: 'overview' | 'access' } | null>(null);
  folderPeek = input<FolderPeekResponseDto | null>(null);
  isOrgSpaceInput = input<boolean | undefined>(undefined);

  closed = output<void>();

  readonly heroIconClass = computed(() => {
    return this.item()?.type === 'folder' ? 'hero-folder' : 'hero-doc';
  });

  readonly isOrgSpace = computed(() => {
    const override = this.isOrgSpaceInput();
    if (override !== undefined) return override;
    const d = this.item()?.data;
    return !!(d?.spaceType === 'ORGANIZATION' || d?.ownerOrgCode || d?.orgCode || this.folderPeek()?.ownerOrgCode);
  });

  readonly itemName = computed(() => {
    const d = this.item()?.data;
    return d?.fileName || d?.name || 'Untitled';
  });

  readonly itemType = computed(() => {
    const d = this.item()?.data;
    return d?.contentType || d?.mimeType || '';
  });

  readonly itemSize = computed(() => {
    const d = this.item()?.data;
    return d?.fileSize ?? d?.fileSizeBytes ?? 0;
  });

  readonly fileCategory = computed(() => {
    const mime = (this.itemType() || '').toLowerCase();
    const ext = (this.itemName() || '').split('.').pop()?.toLowerCase() ?? '';
    if (mime.includes('pdf') || ext === 'pdf') return 'pdf';
    if (mime.includes('presentation') || mime.includes('powerpoint') || ['ppt', 'pptx'].includes(ext)) return 'ppt';
    if (mime.includes('image') || ['png', 'jpg', 'jpeg', 'svg', 'webp', 'gif'].includes(ext)) return 'image';
    if (['js', 'ts', 'html', 'css', 'json', 'java', 'py', 'sh'].includes(ext)) return 'code';
    if (mime.includes('spreadsheet') || mime.includes('excel') || ['csv', 'xlsx', 'xls'].includes(ext)) return 'spreadsheet';
    return 'default';
  });

  readonly fileCategoryLabel = computed(() => {
    const cat = this.fileCategory();
    if (cat === 'pdf') return 'PDF Document';
    if (cat === 'ppt') return 'Presentation';
    if (cat === 'image') return 'Image File';
    if (cat === 'code') return 'Code File';
    if (cat === 'spreadsheet') return 'Spreadsheet';
    return 'Document';
  });

  readonly locationPath = computed(() => {
    const d = this.item()?.data;
    if (!d) return 'Workspace';
    if (d.parentFolderPath) return d.parentFolderPath;
    if (d.path) return d.path;
    if (d.parentPath) return d.parentPath;
    if (d.folderName) return `Workspace / ${d.folderName}`;
    return 'Workspace';
  });

  readonly locationBreadcrumbs = computed(() => {
    const p = this.locationPath();
    const raw = p.split('/').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
    if (raw.length === 0) return ['Workspace'];
    if (raw[0].toLowerCase() !== 'workspace' && raw[0].toLowerCase() !== 'root') {
      return ['Workspace', ...raw];
    }
    return raw;
  });

  readonly formattedSize = computed(() => {
    const bytes = this.item()?.type === 'folder'
      ? (this.folderPeek()?.totalSizeBytes || this.itemSize())
      : this.itemSize();
    return this.formatBytes(bytes);
  });

  private formatBytes(bytes: number): string {
    if (!bytes) return '0 B';
    const k = 1024, s = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + s[i];
  }
}
