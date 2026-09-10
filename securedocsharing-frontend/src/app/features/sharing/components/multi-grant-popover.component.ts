import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  computed,
  HostListener,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedItem } from '../models/sharing.model';
import { IconComponent, IconName } from '../../../shared/components/icon/icon.component';
import { formatSharingDate } from '../utils/sharing-adapter.util';

@Component({
  selector: 'app-multi-grant-popover',
  standalone: true,
  imports: [CommonModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (open() && item()) {
      <div class="popover-backdrop" (click)="closed.emit()">
        <div
          class="popover-panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="multi-grant-title"
          (click)="$event.stopPropagation()"
        >
          <!-- Header -->
          <div class="popover-header">
            <div class="header-left">
              <div class="header-icon-box">
                <app-icon name="layers" [size]="18" />
              </div>
              <div>
                <h3 id="multi-grant-title" class="popover-title">Access Provenance</h3>
                <p class="popover-subtitle">
                  {{ (item()?.matchingGrantCount ?? 1) }} contributing grants for
                  <span class="item-name-highlight">{{ item()?.name }}</span>
                </p>
              </div>
            </div>
            <button
              type="button"
              class="close-btn"
              (click)="closed.emit()"
              aria-label="Close popover"
            >
              <app-icon name="x" [size]="16" />
            </button>
          </div>

          <!-- Body -->
          <div class="popover-body">
            <!-- Effective Access Banner -->
            <div class="effective-access-card">
              <div class="effective-left">
                <span class="effective-label">Effective Permission</span>
                <div class="effective-badge" [class]="effectiveBadgeClass()">
                  <app-icon [name]="effectiveIcon()" [size]="14" />
                  <span>{{ item()?.permissionLevel }}</span>
                </div>
              </div>
              <div class="effective-explanation">
                SecureDocSharing applies the highest granted tier across all explicit channels
                (<strong>EDIT</strong> &gt; <strong>DOWNLOAD</strong> &gt; <strong>VIEW</strong>).
              </div>
            </div>

            <!-- Detailed Provenance Description if present -->
            @if (item()?.sourceDescription) {
              <div class="description-callout">
                <app-icon name="info" [size]="14" class="info-icon" />
                <span class="description-text">{{ item()?.sourceDescription }}</span>
              </div>
            }

            <!-- Contributing Sources List -->
            <div class="contributing-section">
              <span class="section-title">Contributing Share Channels</span>
              <div class="channel-list">
                @for (grant of grantChannels(); track $index) {
                  <div class="channel-card">
                    <div class="channel-icon-pill" [class]="grant.pillClass">
                      <app-icon [name]="grant.icon" [size]="14" />
                    </div>
                    <div class="channel-details">
                      <div class="channel-title-row">
                        <span class="channel-name">{{ grant.title }}</span>
                        <span class="channel-level-badge" [class]="grant.levelClass">
                          {{ grant.level }}
                        </span>
                      </div>
                      <p class="channel-desc">{{ grant.description }}</p>
                    </div>
                  </div>
                }
              </div>
            </div>

            <!-- Grant Metadata Box -->
            <div class="meta-box">
              <div class="meta-row">
                <span class="meta-label">Granted By</span>
                <span class="meta-value">{{ item()?.grantedByName || item()?.grantedByUserEmail || '—' }}</span>
              </div>
              <div class="meta-row">
                <span class="meta-label">Date Granted</span>
                <span class="meta-value">{{ formatSharingDate(item()?.grantedAt) }}</span>
              </div>
              @if (item()?.expiresAt) {
                <div class="meta-row">
                  <span class="meta-label">Expiration</span>
                  <span class="meta-value text-amber-600">{{ formatSharingDate(item()?.expiresAt) }}</span>
                </div>
              }
              @if (item()?.matchedPermissionIds && item()!.matchedPermissionIds!.length > 0) {
                <div class="meta-row">
                  <span class="meta-label">Permission IDs</span>
                  <span class="meta-value tabular">#{{ item()!.matchedPermissionIds!.join(', #') }}</span>
                </div>
              }
            </div>
          </div>

          <!-- Footer -->
          <div class="popover-footer">
            <button type="button" class="btn btn-secondary" (click)="closed.emit()">
              Close
            </button>
            <button
              type="button"
              class="btn btn-primary"
              (click)="onOpenFullAccess()"
            >
              <app-icon name="shield-check" [size]="14" />
              <span>Inspect Security Details</span>
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .popover-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.45);
      backdrop-filter: blur(2px);
      z-index: 9999;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 16px;
      animation: fadeIn 0.15s ease-out;
    }

    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    .popover-panel {
      width: 100%;
      max-width: 520px;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
      display: flex;
      flex-direction: column;
      overflow: hidden;
      animation: scaleIn 0.18s cubic-bezier(0.16, 1, 0.3, 1);
    }

    @keyframes scaleIn {
      from { transform: scale(0.96); opacity: 0; }
      to { transform: scale(1); opacity: 1; }
    }

    /* Header */
    .popover-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px 20px;
      border-bottom: 1px solid #f1f5f9;
      background: #f8fafc;
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .header-icon-box {
      width: 36px;
      height: 36px;
      border-radius: 8px;
      background: var(--sd-color-primary-subtle, #fff7ed);
      color: var(--sd-color-primary, #f97316);
      border: 1px solid var(--sd-color-primary-border, #fed7aa);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .popover-title {
      font-size: 15px;
      font-weight: 600;
      color: #0f172a;
      margin: 0;
      line-height: 1.2;
    }

    .popover-subtitle {
      font-size: 12px;
      color: #64748b;
      margin: 2px 0 0 0;
    }

    .item-name-highlight {
      font-weight: 600;
      color: #1e293b;
    }

    .close-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      border-radius: 6px;
      border: none;
      background: transparent;
      color: #64748b;
      cursor: pointer;
      transition: background 0.15s, color 0.15s;
    }

    .close-btn:hover {
      background: #e2e8f0;
      color: #0f172a;
    }

    /* Body */
    .popover-body {
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      max-height: 70vh;
      overflow-y: auto;
    }

    /* Effective access card */
    .effective-access-card {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 12px 16px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
    }

    .effective-left {
      display: flex;
      flex-direction: column;
      gap: 4px;
      flex-shrink: 0;
    }

    .effective-label {
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #64748b;
    }

    .effective-badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 3px 10px;
      border-radius: 9999px;
      font-size: 12px;
      font-weight: 700;
      width: fit-content;
    }

    .badge-edit {
      background: #fef3c7;
      color: #92400e;
      border: 1px solid #fde68a;
    }

    .badge-download {
      background: #eff6ff;
      color: #1d4ed8;
      border: 1px solid #bfdbfe;
    }

    .badge-view {
      background: #f1f5f9;
      color: #334155;
      border: 1px solid #cbd5e1;
    }

    .effective-explanation {
      font-size: 12px;
      color: #475569;
      line-height: 1.4;
      border-left: 1px solid #e2e8f0;
      padding-left: 12px;
    }

    /* Description callout */
    .description-callout {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      padding: 10px 12px;
      background: #eff6ff;
      border: 1px solid #bfdbfe;
      border-radius: 6px;
      font-size: 12px;
      color: #1e40af;
      line-height: 1.4;
    }

    .info-icon {
      flex-shrink: 0;
      margin-top: 1px;
    }

    /* Channels section */
    .contributing-section {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .section-title {
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: #64748b;
    }

    .channel-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .channel-card {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 10px 12px;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      background: #ffffff;
      transition: border-color 0.15s;
    }

    .channel-card:hover {
      border-color: #cbd5e1;
    }

    .channel-icon-pill {
      width: 28px;
      height: 28px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .pill-direct {
      background: #ecfdf5;
      color: #047857;
    }

    .pill-team {
      background: #eff6ff;
      color: #1d4ed8;
    }

    .pill-org {
      background: #fff7ed;
      color: #c2410c;
    }

    .pill-collab {
      background: #faf5ff;
      color: #7e22ce;
    }

    .pill-generic {
      background: #f1f5f9;
      color: #475569;
    }

    .channel-details {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .channel-title-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }

    .channel-name {
      font-size: 13px;
      font-weight: 600;
      color: #1e293b;
    }

    .channel-level-badge {
      font-size: 10.5px;
      font-weight: 700;
      padding: 1px 6px;
      border-radius: 4px;
      text-transform: uppercase;
    }

    .channel-desc {
      font-size: 11.5px;
      color: #64748b;
      margin: 0;
      line-height: 1.35;
    }

    /* Metadata Box */
    .meta-box {
      display: flex;
      flex-direction: column;
      gap: 6px;
      padding: 10px 14px;
      background: #f8fafc;
      border: 1px solid #f1f5f9;
      border-radius: 8px;
      font-size: 12px;
    }

    .meta-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .meta-label {
      color: #64748b;
    }

    .meta-value {
      font-weight: 500;
      color: #1e293b;
    }

    .tabular {
      font-variant-numeric: tabular-nums;
      font-family: monospace;
      font-size: 11.5px;
    }

    /* Footer */
    .popover-footer {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 10px;
      padding: 12px 20px;
      border-top: 1px solid #f1f5f9;
      background: #f8fafc;
    }

    .btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 7px 14px;
      border-radius: 6px;
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.15s ease;
      border: 1px solid transparent;
    }

    .btn-secondary {
      background: #ffffff;
      border-color: #cbd5e1;
      color: #334155;
    }

    .btn-secondary:hover {
      background: #f8fafc;
      border-color: #94a3b8;
      color: #0f172a;
    }

    .btn-primary {
      background: var(--sd-color-primary, #f97316);
      color: #ffffff;
      box-shadow: 0 1px 2px rgba(249, 115, 22, 0.2);
    }

    .btn-primary:hover {
      background: var(--sd-color-primary-hover, #ea580c);
      box-shadow: 0 2px 4px rgba(249, 115, 22, 0.3);
    }
  `],
})
export class MultiGrantPopoverComponent {
  item = input<SharedItem | null>(null);
  open = input<boolean>(false);

  closed = output<void>();
  manageAccess = output<SharedItem>();

  formatSharingDate = formatSharingDate;

  @HostListener('window:keydown.escape')
  onEscapeKey(): void {
    if (this.open()) {
      this.closed.emit();
    }
  }

  readonly effectiveBadgeClass = computed(() => {
    const level = this.item()?.permissionLevel;
    if (level === 'EDIT' || level === 'ADMIN') return 'badge-edit';
    if (level === 'DOWNLOAD') return 'badge-download';
    return 'badge-view';
  });

  readonly effectiveIcon = computed<IconName>(() => {
    const level = this.item()?.permissionLevel;
    if (level === 'EDIT' || level === 'ADMIN') return 'edit-3';
    if (level === 'DOWNLOAD') return 'download';
    return 'eye';
  });

  readonly grantChannels = computed(() => {
    const item = this.item();
    if (!item) return [];

    const channels: Array<{
      title: string;
      description: string;
      level: string;
      icon: IconName;
      pillClass: string;
      levelClass: string;
    }> = [];

    const labels = item.sourceLabels && item.sourceLabels.length > 0
      ? item.sourceLabels
      : [item.sourceLabel || 'DIRECT SHARE'];

    for (const rawLabel of labels) {
      const label = (rawLabel || '').toUpperCase();
      if (label.includes('DIRECT')) {
        channels.push({
          title: 'Direct Personal Share',
          description: 'Explicit share granted directly to your user identity.',
          level: item.permissionLevel,
          icon: 'user',
          pillClass: 'pill-direct',
          levelClass: this.getLevelBadgeClass(item.permissionLevel),
        });
      } else if (label.includes('TEAM')) {
        channels.push({
          title: item.targetGroupName ? `Team: ${item.targetGroupName}` : 'Team Share',
          description: 'Explicit share targeting a team in which you hold active membership.',
          level: item.permissionLevel,
          icon: 'users-round',
          pillClass: 'pill-team',
          levelClass: this.getLevelBadgeClass(item.permissionLevel),
        });
      } else if (label.includes('ORGANIZATION') || label.includes('ORG')) {
        channels.push({
          title: item.targetOrgName
            ? `Organization: ${item.targetOrgName}`
            : item.targetOrgCode
              ? `Organization: ${item.targetOrgCode}`
              : 'Organization Share',
          description: 'Explicit share targeting an organization of which you are an active member.',
          level: item.permissionLevel,
          icon: 'building-2',
          pillClass: 'pill-org',
          levelClass: this.getLevelBadgeClass(item.permissionLevel),
        });
      } else if (label.includes('COLLABORATION')) {
        channels.push({
          title: item.collaborationName ? `Collaboration: ${item.collaborationName}` : 'Collaboration Share',
          description: 'Explicit share targeting an active external collaboration where you are a participant.',
          level: item.permissionLevel,
          icon: 'share-2',
          pillClass: 'pill-collab',
          levelClass: this.getLevelBadgeClass(item.permissionLevel),
        });
      } else {
        channels.push({
          title: rawLabel,
          description: 'Explicit resource access grant.',
          level: item.permissionLevel,
          icon: 'layers',
          pillClass: 'pill-generic',
          levelClass: this.getLevelBadgeClass(item.permissionLevel),
        });
      }
    }

    return channels;
  });

  private getLevelBadgeClass(level: string): string {
    if (level === 'EDIT' || level === 'ADMIN') return 'badge-edit';
    if (level === 'DOWNLOAD') return 'badge-download';
    return 'badge-view';
  }

  onOpenFullAccess(): void {
    const it = this.item();
    this.closed.emit();
    if (it) {
      this.manageAccess.emit(it);
    }
  }
}
