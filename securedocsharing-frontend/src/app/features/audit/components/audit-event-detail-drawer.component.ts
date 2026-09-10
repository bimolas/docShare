import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  HostListener,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { UnifiedAuditEventResponseDto } from '../../../core/models/unified-audit.model';
import { IconComponent } from '../../../shared/components/icon/icon.component';
import { formatActionDisplay, formatDetailsText } from '../utils/audit-format.util';

@Component({
  selector: 'app-audit-event-detail-drawer',
  standalone: true,
  imports: [CommonModule, DatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen() && event(); as ev) {
      <div class="drawer-backdrop" (click)="onBackdropClick($event)">
        <div
          class="drawer-panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="audit-drawer-title"
        >
          <!-- ── Header ────────────────────────────────────── -->
          <div class="drawer-header">
            <div class="header-left">
              <div class="header-pre">
                <span class="event-id font-mono">{{ ev.id }}</span>
                <span class="source-badge font-mono">{{ ev.source }}</span>
              </div>
              <h2 id="audit-drawer-title" class="drawer-title">
                {{ ev.actionDisplay || ev.action }}
              </h2>
            </div>
            <div class="header-right">
              <span
                class="status-pill"
                [ngClass]="'status-' + (ev.result?.status || 'success').toLowerCase()"
              >
                {{ ev.result?.status || 'SUCCESS' }}
              </span>
              <button
                type="button"
                class="close-btn"
                (click)="close.emit()"
                aria-label="Close event details"
              >
                <app-icon name="x" [size]="16" />
              </button>
            </div>
          </div>

          <!-- ── Body ──────────────────────────────────────── -->
          <div class="drawer-body">
            <!-- 1. WHO (Actor) -->
            <section class="detail-card">
              <h3 class="card-heading">
                <app-icon name="user" [size]="15" />
                <span>Who (Actor Attribution)</span>
              </h3>
              <div class="grid-meta">
                <div class="meta-row">
                  <span class="meta-label">Display Name</span>
                  <span class="meta-val font-medium">
                    {{ ev.actor?.displayName || ev.actor?.email || 'System' }}
                  </span>
                </div>
                @if (ev.actor?.email) {
                  <div class="meta-row">
                    <span class="meta-label">Email Address</span>
                    <span class="meta-val font-mono text-xs">{{ ev.actor?.email }}</span>
                  </div>
                }
                @if (ev.actor?.userId) {
                  <div class="meta-row">
                    <span class="meta-label">User ID</span>
                    <span class="meta-val font-mono text-xs text-slate-500">{{ ev.actor?.userId }}</span>
                  </div>
                }
                @if (ev.actor?.impersonatedBy) {
                  <div class="meta-row">
                    <span class="meta-label">Impersonated By</span>
                    <span class="meta-val font-mono text-xs text-amber-700 bg-amber-50 px-1.5 py-0.5 rounded">
                      {{ ev.actor?.impersonatedBy }}
                    </span>
                  </div>
                }
              </div>
            </section>

            <!-- 2. WHAT & TARGET -->
            <section class="detail-card">
              <h3 class="card-heading">
                <app-icon name="file-text" [size]="15" />
                <span>What (Target Resource & Action)</span>
              </h3>
              <div class="grid-meta">
                <div class="meta-row">
                  <span class="meta-label">Action</span>
                  <span class="meta-val font-medium">{{ formatActionDisplay(ev) }}</span>
                </div>
                <div class="meta-row">
                  <span class="meta-label">Category</span>
                  <span class="meta-val">{{ getCategoryDisplay(ev.category) }}</span>
                </div>
                @if (ev.target; as t) {
                  @if (t.type) {
                    <div class="meta-row">
                      <span class="meta-label">Resource Type</span>
                      <span class="meta-val">{{ t.type }}</span>
                    </div>
                  }
                  @if (t.name) {
                    <div class="meta-row">
                      <span class="meta-label">Resource Name</span>
                      <span class="meta-val font-medium text-slate-900">{{ t.name }}</span>
                    </div>
                  }
                  @if (t.id) {
                    <div class="meta-row">
                      <span class="meta-label">Resource ID</span>
                      <span class="meta-val font-mono text-xs">{{ t.id }}</span>
                    </div>
                  }
                  @if (t.fileHash) {
                    <div class="meta-row">
                      <span class="meta-label">SHA-256 Hash</span>
                      <div class="hash-copy-block font-mono">
                        <span>{{ t.fileHash }}</span>
                        <button
                          type="button"
                          class="copy-btn"
                          (click)="copyToClipboard(t.fileHash)"
                          title="Copy file hash"
                        >
                          <app-icon [name]="copiedHash() ? 'check' : 'copy'" [size]="12" />
                        </button>
                      </div>
                    </div>
                  }
                  @if (t.fileSize) {
                    <div class="meta-row">
                      <span class="meta-label">File Size</span>
                      <span class="meta-val font-tabular">{{ formatBytes(t.fileSize) }}</span>
                    </div>
                  }
                }
              </div>
            </section>

            <!-- 3. WHERE (Location & Context) -->
            <section class="detail-card">
              <h3 class="card-heading">
                <app-icon name="folder" [size]="15" />
                <span>Where (Contextual Scope & Location)</span>
              </h3>
              <div class="grid-meta">
                <div class="meta-row">
                  <span class="meta-label">Space Type</span>
                  <span class="meta-val">{{ ev.location?.spaceType || 'PERSONAL' }}</span>
                </div>
                @if (ev.location?.orgCode) {
                  <div class="meta-row">
                    <span class="meta-label">Organization</span>
                    <span class="meta-val font-medium">{{ ev.location?.orgCode }}</span>
                  </div>
                }
                @if (ev.location?.workspaceName || ev.location?.workspaceId) {
                  <div class="meta-row">
                    <span class="meta-label">Workspace</span>
                    <span class="meta-val">
                      {{ ev.location?.workspaceName || '#' + ev.location?.workspaceId }}
                    </span>
                  </div>
                }
                @if (ev.location?.folderName || ev.location?.folderId) {
                  <div class="meta-row">
                    <span class="meta-label">Folder</span>
                    <span class="meta-val">
                      {{ ev.location?.folderName || '#' + ev.location?.folderId }}
                    </span>
                  </div>
                }
                @if (ev.location?.pathSnapshot) {
                  <div class="meta-row">
                    <span class="meta-label">Path Snapshot</span>
                    <span class="meta-val font-mono text-xs text-slate-600 bg-slate-50 p-1 rounded">
                      {{ ev.location?.pathSnapshot }}
                    </span>
                  </div>
                }
              </div>
            </section>

            <!-- 4. WHAT CHANGED (State Transitions & Justifications) -->
            @if (ev.changes?.previousValue || ev.changes?.newValue || ev.changes?.reason || ev.changes?.details) {
              <section class="detail-card">
                <h3 class="card-heading">
                  <app-icon name="refresh-cw" [size]="15" />
                  <span>State Changes & Provenance</span>
                </h3>

                @if (ev.changes?.previousValue || ev.changes?.newValue) {
                  <div class="diff-box">
                    <div class="diff-pane">
                      <span class="diff-label">Previous Value</span>
                      <div class="diff-content font-mono">
                        {{ ev.changes?.previousValue || '(None)' }}
                      </div>
                    </div>
                    <div class="diff-arrow-wrap">→</div>
                    <div class="diff-pane">
                      <span class="diff-label">New Value</span>
                      <div class="diff-content font-mono active">
                        {{ ev.changes?.newValue || '(None)' }}
                      </div>
                    </div>
                  </div>
                }

                @if (ev.changes?.reason) {
                  <div class="note-box">
                    <span class="note-label">Requester Justification / Reason</span>
                    <p class="note-text">{{ ev.changes?.reason }}</p>
                  </div>
                }

                @if (ev.changes?.details) {
                  <div class="note-box">
                    <span class="note-label">Action Details</span>
                    <p class="note-text">{{ formatDetailsText(ev.changes?.details) }}</p>
                  </div>
                }
              </section>
            }

            <!-- 5. WHEN (Timestamp) -->
            <section class="detail-card">
              <h3 class="card-heading">
                <app-icon name="clock" [size]="15" />
                <span>When (Timestamp)</span>
              </h3>
              <div class="grid-meta">
                <div class="meta-row">
                  <span class="meta-label">Exact Time</span>
                  <span class="meta-val font-tabular font-mono text-xs">
                    {{ (ev.timestamp | date: 'EEEE, MMMM d, y · h:mm:ss a zzzz') || '—' }}
                  </span>
                </div>
              </div>
            </section>

            <!-- 6. RESULT & TECHNICAL TELEMETRY -->
            <section class="detail-card">
              <h3 class="card-heading">
                <app-icon name="shield-check" [size]="15" />
                <span>Integrity Proof & Technical Telemetry</span>
              </h3>
              <div class="grid-meta">
                <div class="meta-row">
                  <span class="meta-label">Operation Result</span>
                  <span class="meta-val font-semibold">{{ ev.result?.status || 'SUCCESS' }}</span>
                </div>

                @if (ev.result?.blockchainStatus) {
                  <div class="meta-row">
                    <span class="meta-label">Blockchain Anchor</span>
                    <span class="meta-val font-mono text-xs text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded">
                      {{ ev.result?.blockchainStatus }}
                    </span>
                  </div>
                }

                @if (ev.result?.blockchainTxHash) {
                  <div class="meta-row">
                    <span class="meta-label">Blockchain Tx</span>
                    <div class="hash-copy-block font-mono">
                      <span>{{ ev.result?.blockchainTxHash }}</span>
                      <button
                        type="button"
                        class="copy-btn"
                        (click)="copyToClipboard(ev.result?.blockchainTxHash!)"
                        title="Copy Tx Hash"
                      >
                        <app-icon name="copy" [size]="12" />
                      </button>
                    </div>
                  </div>
                }

                @if (ev.metadata?.recordHash) {
                  <div class="meta-row">
                    <span class="meta-label">Record Hash (SHA-256)</span>
                    <div class="hash-copy-block font-mono">
                      <span>{{ ev.metadata?.recordHash }}</span>
                      <button
                        type="button"
                        class="copy-btn"
                        (click)="copyToClipboard(ev.metadata?.recordHash!)"
                        title="Copy record hash"
                      >
                        <app-icon name="copy" [size]="12" />
                      </button>
                    </div>
                  </div>
                }

                @if (ev.metadata?.ipAddress) {
                  <div class="meta-row">
                    <span class="meta-label">Client IP</span>
                    <span class="meta-val font-mono text-xs">{{ ev.metadata?.ipAddress }}</span>
                  </div>
                }

                @if (ev.metadata?.userAgent) {
                  <div class="meta-row">
                    <span class="meta-label">User Agent</span>
                    <span class="meta-val font-mono text-xs text-slate-500">{{ ev.metadata?.userAgent }}</span>
                  </div>
                }

                @if (!ev.metadata?.ipAddress && !ev.metadata?.userAgent) {
                  <div class="redaction-note">
                    <app-icon name="lock-keyhole" [size]="13" />
                    <span>Technical network telemetry (IP / User-Agent) is restricted to Organization Administrators.</span>
                  </div>
                }
              </div>
            </section>
          </div>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .drawer-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(15, 23, 42, 0.35);
        backdrop-filter: blur(2px);
        z-index: 999;
        display: flex;
        justify-content: flex-end;
      }

      .drawer-panel {
        width: 100%;
        max-width: 540px;
        height: 100%;
        background: #ffffff;
        box-shadow: -4px 0 24px rgba(0, 0, 0, 0.12);
        display: flex;
        flex-direction: column;
        animation: slideInRight 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      }

      @keyframes slideInRight {
        from { transform: translateX(100%); }
        to { transform: translateX(0); }
      }

      .drawer-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 1.25rem 1.5rem;
        border-bottom: 1px solid #e2e8f0;
        background: #f8fafc;
      }

      .header-left {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
      }

      .header-pre {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }

      .event-id {
        font-size: 0.75rem;
        font-weight: 700;
        color: #f97316;
      }

      .source-badge {
        font-size: 0.65rem;
        padding: 0.05rem 0.35rem;
        background: #e2e8f0;
        color: #475569;
        border-radius: 3px;
      }

      .drawer-title {
        font-size: 1.05rem;
        font-weight: 600;
        color: #0f172a;
        margin: 0;
      }

      .header-right {
        display: flex;
        align-items: center;
        gap: 0.75rem;
      }

      .status-pill {
        font-size: 0.7rem;
        font-weight: 600;
        padding: 0.2rem 0.5rem;
        border-radius: 9999px;

        &.status-success {
          background: #f0fdf4;
          color: #166534;
          border: 1px solid #bbf7d0;
        }
        &.status-denied, &.status-failed {
          background: #fef2f2;
          color: #991b1b;
          border: 1px solid #fecaca;
        }
        &.status-pending {
          background: #fffbeb;
          color: #92400e;
          border: 1px solid #fde68a;
        }
        &.status-revoked {
          background: #f8fafc;
          color: #475569;
          border: 1px solid #cbd5e1;
        }
      }

      .close-btn {
        background: transparent;
        border: none;
        color: #64748b;
        cursor: pointer;
        padding: 4px;
        border-radius: 4px;
        display: flex;
        align-items: center;
        justify-content: center;

        &:hover {
          background: #e2e8f0;
          color: #0f172a;
        }
      }

      .drawer-body {
        flex: 1;
        overflow-y: auto;
        padding: 1.5rem;
        display: flex;
        flex-direction: column;
        gap: 1.25rem;
      }

      .detail-card {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        padding: 1rem 1.15rem;
      }

      .card-heading {
        display: flex;
        align-items: center;
        gap: 0.45rem;
        font-size: 0.8125rem;
        font-weight: 600;
        color: #334155;
        margin: 0 0 0.85rem 0;
        text-transform: uppercase;
        letter-spacing: 0.03em;
      }

      .grid-meta {
        display: flex;
        flex-direction: column;
        gap: 0.65rem;
      }

      .meta-row {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 1rem;
        font-size: 0.8125rem;
      }

      .meta-label {
        color: #64748b;
        flex-shrink: 0;
        min-width: 120px;
      }

      .meta-val {
        color: #0f172a;
        text-align: right;
        word-break: break-all;
      }

      .hash-copy-block {
        display: flex;
        align-items: center;
        gap: 0.35rem;
        font-size: 0.72rem;
        color: #334155;
        background: #f8fafc;
        padding: 0.15rem 0.4rem;
        border-radius: 4px;
        border: 1px solid #e2e8f0;
        word-break: break-all;
      }

      .copy-btn {
        background: transparent;
        border: none;
        color: #64748b;
        cursor: pointer;
        padding: 2px;
        display: flex;
        align-items: center;

        &:hover {
          color: #f97316;
        }
      }

      .diff-box {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
        padding: 0.75rem;
        margin-bottom: 0.75rem;
      }

      .diff-pane {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
      }

      .diff-label {
        font-size: 0.6875rem;
        font-weight: 600;
        color: #64748b;
        text-transform: uppercase;
      }

      .diff-content {
        font-size: 0.75rem;
        padding: 0.35rem 0.5rem;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 4px;
        color: #64748b;

        &.active {
          color: #166534;
          background: #f0fdf4;
          border-color: #bbf7d0;
          font-weight: 600;
        }
      }

      .diff-arrow-wrap {
        font-size: 1rem;
        color: #94a3b8;
      }

      .note-box {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        margin-top: 0.5rem;
        background: #f8fafc;
        padding: 0.65rem 0.85rem;
        border-radius: 6px;
        border: 1px solid #f1f5f9;
      }

      .note-label {
        font-size: 0.6875rem;
        font-weight: 600;
        color: #475569;
        text-transform: uppercase;
      }

      .note-text {
        font-size: 0.8125rem;
        color: #0f172a;
        margin: 0;
        line-height: 1.4;
      }

      .redaction-note {
        display: flex;
        align-items: center;
        gap: 0.4rem;
        font-size: 0.72rem;
        color: #64748b;
        background: #f8fafc;
        padding: 0.4rem 0.6rem;
        border-radius: 4px;
        border: 1px dashed #cbd5e1;
      }
    `,
  ],
})
export class AuditEventDetailDrawerComponent {
  isOpen = input<boolean>(false);
  event = input<UnifiedAuditEventResponseDto | null>(null);
  close = output<void>();

  copiedHash = signal<boolean>(false);

  @HostListener('keydown.escape')
  handleEscape(): void {
    if (this.isOpen()) {
      this.close.emit();
    }
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('drawer-backdrop')) {
      this.close.emit();
    }
  }

  copyToClipboard(text: string): void {
    if (!text) return;
    navigator.clipboard?.writeText(text).then(() => {
      this.copiedHash.set(true);
      setTimeout(() => this.copiedHash.set(false), 2000);
    });
  }

  formatBytes(bytes?: number): string {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  formatActionDisplay(ev: UnifiedAuditEventResponseDto): string {
    return formatActionDisplay(ev);
  }

  formatDetailsText(details?: string): string {
    return formatDetailsText(details);
  }

  getCategoryDisplay(category?: string): string {
    switch (category) {
      case 'CONTENT': return 'Content & Files';
      case 'ACCESS': return 'Access & Permissions';
      case 'SHARING': return 'External Sharing';
      case 'SECURITY': return 'Security & Audit';
      case 'TEAM': return 'Team Governance';
      case 'WORKSPACE': return 'Workspace Events';
      default: return category || '—';
    }
  }
}
