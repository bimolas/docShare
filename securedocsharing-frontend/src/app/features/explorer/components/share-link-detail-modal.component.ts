import { Component, Input, Output, EventEmitter, inject, signal, OnChanges, SimpleChanges, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ShareLinkResponseDto } from '../../permissions/interfaces/permission.interfaces';
import { ShareApiService } from '../../permissions/services/share-api.service';
import { PermissionApiService } from '../../permissions/services/permission-api.service';
import { ConfirmationModalComponent } from '../../../shared/ui/modal/confirmation-modal.component';
import { ShareItemTarget } from './share-modal.component';
import { IconComponent } from '../../../shared/components/icon/icon.component';

@Component({
  selector: 'app-share-link-detail-modal',
  standalone: true,
  imports: [CommonModule, ConfirmationModalComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen && shareLink) {
      <div class="modal-backdrop" (click)="onClose()">
        <div class="modal-card" (click)="$event.stopPropagation()" role="dialog" aria-modal="true" aria-labelledby="modal-share-title">
          
          <!-- Header -->
          <div class="modal-header">
            <div class="header-top">
              <div class="link-icon-badge">
                <app-icon name="link" [size]="18" />
              </div>
              <div class="header-titles">
                <div class="status-row">
                  <span class="status-pill" [class.active]="shareLink.active" [class.expired]="!shareLink.active">
                    {{ shareLink.active ? 'Active Share Link' : 'Expired Link' }}
                  </span>
                  <span class="level-badge">
                    {{ shareLink.permissionLevel }}
                  </span>
                  @if (shareLink.passwordProtected) {
                    <span class="password-badge">
                      <app-icon name="lock-keyhole" [size]="12" />
                      Password Protected
                    </span>
                  }
                </div>
                <h3 id="modal-share-title" class="modal-title">Share Link Details</h3>
                @if (getItemName()) {
                  <span class="item-subtitle">Resource: <strong>{{ getItemName() }}</strong></span>
                }
              </div>
              <button type="button" class="btn-close-modal" (click)="onClose()" aria-label="Close modal">
                <app-icon name="x" [size]="18" />
              </button>
            </div>
          </div>

          <!-- Body -->
          <div class="modal-body">

            @if (errorMessage()) {
              <div class="alert-banner error">
                <app-icon name="alert-circle" [size]="16" />
                <span>{{ errorMessage() }}</span>
              </div>
            }

            @if (canReveal()) {
              <!-- COPY LINK URL SECTION -->
              <div class="link-url-card">
                <label class="card-label">Share Link URL</label>
                @if (shareLink.token) {
                  <div class="url-input-group">
                    <input type="text" class="url-text-field" [value]="getShareUrl()" readonly (click)="copyUrl()" />
                    <button type="button" class="btn-copy-url" (click)="copyUrl()">
                      @if (copied()) {
                        <app-icon name="check" [size]="14" />
                        <span>Copied!</span>
                      } @else {
                        <app-icon name="copy" [size]="14" />
                        <span>Copy</span>
                      }
                    </button>
                  </div>
                } @else {
                  <div class="reveal-prompt-card">
                    <p class="reveal-hint">This link's bearer secret is protected. As the link creator, you can reveal and copy the URL on demand.</p>
                    <button type="button" class="btn-reveal-action" [disabled]="isRevealing()" (click)="revealAndCopy()">
                      @if (isRevealing()) {
                        <span class="spinner-sm"></span>
                        <span>Revealing...</span>
                      } @else {
                        <app-icon name="key" [size]="14" />
                        <span>Reveal & Copy URL</span>
                      }
                    </button>
                  </div>
                }
              </div>

              @if (shareLink.token) {
                <!-- SOCIAL MEDIA / PLATFORM SHARE OPTIONS -->
                <div class="social-share-section">
                  <div class="section-title">
                    <app-icon name="share-2" [size]="14" />
                    <span>Share via Platform</span>
                  </div>
                  
                  <div class="social-grid">
                    <!-- WhatsApp -->
                    <button type="button" class="social-btn" (click)="shareTo('whatsapp')" title="Share on WhatsApp">
                      <div class="social-icon">
                        <app-icon name="brand-whatsapp" [size]="18" />
                      </div>
                      <span class="social-name">WhatsApp</span>
                    </button>

                    <!-- Telegram -->
                    <button type="button" class="social-btn" (click)="shareTo('telegram')" title="Share on Telegram">
                      <div class="social-icon">
                        <app-icon name="brand-telegram" [size]="18" />
                      </div>
                      <span class="social-name">Telegram</span>
                    </button>

                    <!-- X / Twitter -->
                    <button type="button" class="social-btn" (click)="shareTo('twitter')" title="Share on X (Twitter)">
                      <div class="social-icon">
                        <app-icon name="brand-x" [size]="18" />
                      </div>
                      <span class="social-name">X</span>
                    </button>

                    <!-- LinkedIn -->
                    <button type="button" class="social-btn" (click)="shareTo('linkedin')" title="Share on LinkedIn">
                      <div class="social-icon">
                        <app-icon name="brand-linkedin" [size]="18" />
                      </div>
                      <span class="social-name">LinkedIn</span>
                    </button>

                    <!-- Facebook -->
                    <button type="button" class="social-btn" (click)="shareTo('facebook')" title="Share on Facebook">
                      <div class="social-icon">
                        <app-icon name="brand-facebook" [size]="18" />
                      </div>
                      <span class="social-name">Facebook</span>
                    </button>

                    <!-- Email -->
                    <button type="button" class="social-btn" (click)="shareTo('email')" title="Send via Email">
                      <div class="social-icon">
                        <app-icon name="mail" [size]="18" />
                      </div>
                      <span class="social-name">Email</span>
                    </button>

                    <!-- QR Code Toggle -->
                    <button type="button" class="social-btn qrcode" (click)="toggleQrCode()" [class.active]="showQrCode()" title="Show QR Code">
                      <div class="social-icon">
                        <app-icon name="qr-code" [size]="18" />
                      </div>
                      <span class="social-name">QR Code</span>
                    </button>
                  </div>

                  <!-- Inline QR Code Display -->
                  @if (showQrCode()) {
                    <div class="qr-code-card">
                      <div class="qr-code-header">
                        <span>Scan with Mobile Camera</span>
                        <button type="button" class="btn-close-qr" (click)="showQrCode.set(false)" aria-label="Hide QR Code">
                          <app-icon name="x" [size]="14" />
                        </button>
                      </div>
                      <div class="qr-image-wrapper">
                        <img [src]="getQrCodeUrl()" alt="Share Link QR Code" class="qr-image" />
                      </div>
                      <span class="qr-subtext">Anyone with this QR code can access the link</span>
                    </div>
                  }
                </div>
              }
            } @else {
              <!-- Administrator Audit Notice -->
              <div class="security-audit-notice">
                <div class="notice-icon">
                  <app-icon name="shield" [size]="20" />
                </div>
                <div class="notice-content">
                  <h4>Bearer Secret Hidden</h4>
                  <p>For zero-trust security, bearer secrets are never exposed to non-creator administrators. You can audit this link's parameters and revoke it below, but only the original creator can reveal the URL.</p>
                </div>
              </div>
            }

            <!-- PARAMETERS GRID -->
            <div class="meta-section-card">
              <div class="section-title">
                <app-icon name="clock" [size]="14" />
                <span>Link Parameters & Time limits</span>
              </div>

              <div class="meta-grid">
                <div class="meta-item">
                  <span class="meta-label">Permission Level</span>
                  <span class="meta-value highlight">{{ shareLink.permissionLevel }}</span>
                </div>

                <div class="meta-item">
                  <span class="meta-label">Time Remaining</span>
                  <span class="meta-value">{{ formatRemainingTime(shareLink.expiresAt) }}</span>
                </div>

                <div class="meta-item">
                  <span class="meta-label">Created At</span>
                  <span class="meta-value">{{ formatDate(shareLink.createdAt) }}</span>
                </div>

                <div class="meta-item">
                  <span class="meta-label">Expiration Date</span>
                  <span class="meta-value">{{ formatDate(shareLink.expiresAt) }}</span>
                </div>
              </div>
            </div>

          </div>

          <!-- Footer -->
          <div class="modal-footer">
            @if (shareLink.id) {
              <button 
                type="button" 
                class="btn-revoke-danger"
                [disabled]="isRevoking()"
                (click)="promptRevokeShareLink()"
              >
                @if (isRevoking()) {
                  <span class="spinner-sm"></span>
                  <span>Revoking...</span>
                } @else {
                  <app-icon name="trash-2" [size]="14" />
                  <span>Revoke Share Link</span>
                }
              </button>
            }

            <button type="button" class="btn-cancel" (click)="onClose()">
              Close
            </button>
          </div>

          <sd-confirmation-modal
            [open]="showRevokeConfirm()"
            title="Revoke Share Link"
            message="Are you sure you want to revoke this share link? Anyone with this link will immediately lose access to this resource."
            confirmText="Revoke Share Link"
            cancelText="Cancel"
            variant="danger"
            [loading]="isRevoking()"
            (confirmed)="confirmRevokeShareLink()"
            (cancelled)="showRevokeConfirm.set(false)"
          />

        </div>
      </div>
    }
  `,
  styles: [`
    .modal-backdrop {
      position: fixed; inset: 0;
      background: rgba(15, 23, 42, 0.45);
      backdrop-filter: blur(8px);
      z-index: var(--sd-z-drawer-backdrop, 2200);
      display: flex; align-items: center; justify-content: center;
      padding: 1.25rem;
      animation: fadeIn 0.15s ease-out;
    }
    @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

    .modal-card {
      width: 100%; max-width: 520px;
      background: #ffffff;
      border-radius: 1rem;
      box-shadow: 0 20px 25px -5px rgba(15, 23, 42, 0.1), 0 8px 10px -6px rgba(15, 23, 42, 0.05);
      display: flex; flex-direction: column;
      overflow: hidden;
      border: 1px solid #e2e8f0;
      animation: scaleUp 0.18s cubic-bezier(0.16, 1, 0.3, 1);
    }
    @keyframes scaleUp {
      from { transform: scale(0.96); opacity: 0; }
      to   { transform: scale(1); opacity: 1; }
    }

    .modal-header {
      padding: 1.25rem 1.5rem;
      border-bottom: 1px solid #f1f5f9;
      background: #ffffff;
    }
    .header-top {
      display: flex; align-items: flex-start; gap: 0.875rem;
    }
    .link-icon-badge {
      width: 2.5rem; height: 2.5rem; border-radius: 0.625rem;
      background: #f8fafc; color: #0f172a; border: 1px solid #e2e8f0;
      box-shadow: 0 1px 2px 0 rgba(15, 23, 42, 0.04);
      display: flex; align-items: center; justify-content: center;
      flex-shrink: 0;
    }

    .header-titles {
      flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 0.25rem;
    }
    .status-row {
      display: flex; align-items: center; gap: 0.375rem; flex-wrap: wrap;
    }
    .status-pill {
      font-size: 0.6875rem; font-weight: 600;
      padding: 0.1875rem 0.5rem; border-radius: 9999px;
      display: inline-flex; align-items: center; gap: 0.375rem;
      letter-spacing: 0.01em;
    }
    .status-pill.active {
      background: #f0fdf4; color: #166534; border: 1px solid #bbf7d0;
    }
    .status-pill.active::before {
      content: ''; width: 0.375rem; height: 0.375rem; border-radius: 50%; background: #16a34a;
    }
    .status-pill.expired {
      background: #fef2f2; color: #991b1b; border: 1px solid #fecaca;
    }
    .status-pill.expired::before {
      content: ''; width: 0.375rem; height: 0.375rem; border-radius: 50%; background: #dc2626;
    }

    .level-badge {
      font-size: 0.6875rem; font-weight: 600; padding: 0.1875rem 0.5rem;
      border-radius: 0.375rem; background: #f8fafc; color: #475569;
      border: 1px solid #e2e8f0; text-transform: uppercase; letter-spacing: 0.02em;
    }
    .password-badge {
      font-size: 0.6875rem; font-weight: 600; padding: 0.1875rem 0.5rem;
      border-radius: 0.375rem; background: #fffbeb; color: #92400e;
      border: 1px solid #fef3c7; display: inline-flex; align-items: center; gap: 0.25rem;
    }

    .modal-title {
      font-size: 1.0625rem; font-weight: 600; color: #0f172a; margin: 0; letter-spacing: -0.01em;
    }
    .item-subtitle {
      font-size: 0.8125rem; color: #64748b; margin-top: 0.125rem; font-weight: 400;
    }
    .item-subtitle strong { color: #1e293b; font-weight: 600; }

    .btn-close-modal {
      background: transparent; border: none; cursor: pointer; color: #94a3b8;
      padding: 0.375rem; border-radius: 0.375rem; display: flex; align-items: center;
      transition: all 0.15s ease;
    }
    .btn-close-modal:hover { background: #f1f5f9; color: #0f172a; }

    .modal-body {
      padding: 1.25rem 1.5rem;
      display: flex; flex-direction: column; gap: 1rem;
      max-height: 70vh; overflow-y: auto;
    }

    .alert-banner {
      display: flex; align-items: center; gap: 0.5rem;
      padding: 0.625rem 0.875rem; border-radius: 0.5rem; font-size: 0.8125rem; font-weight: 500;
    }
    .alert-banner.error { background: #fef2f2; color: #991b1b; border: 1px solid #fee2e2; }

    .link-url-card {
      display: flex; flex-direction: column; gap: 0.375rem;
    }
    .card-label { font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; color: #64748b; }
    .url-input-group {
      display: flex; gap: 0.5rem;
    }
    .url-text-field {
      flex: 1; padding: 0.5625rem 0.875rem; border-radius: 0.5rem;
      border: 1px solid #e2e8f0; background: #f8fafc; font-size: 0.8125rem;
      color: #0f172a; outline: none; font-family: var(--sd-font-mono, monospace); cursor: pointer;
      transition: all 0.15s ease;
    }
    .url-text-field:focus { border-color: #0f172a; background: #ffffff; }
    
    .btn-copy-url {
      display: flex; align-items: center; justify-content: center; gap: 0.375rem;
      padding: 0.5625rem 1rem; border-radius: 0.5rem; border: none;
      background: #0f172a; color: #ffffff; font-size: 0.8125rem; font-weight: 500;
      cursor: pointer; transition: all 0.15s ease; flex-shrink: 0;
      box-shadow: 0 1px 2px 0 rgba(15, 23, 42, 0.05); min-width: 5.5rem;
    }
    .btn-copy-url:hover { background: #1e293b; }

    .reveal-prompt-card {
      display: flex; flex-direction: column; gap: 0.625rem;
      padding: 0.875rem 1rem; border-radius: 0.5rem;
      background: #f8fafc; border: 1px solid #e2e8f0;
    }
    .reveal-hint {
      font-size: 0.8125rem; color: #475569; margin: 0; line-height: 1.4;
    }
    .btn-reveal-action {
      align-self: flex-start; display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.5rem 0.875rem; border-radius: 0.5rem; border: 1px solid #0284c7;
      background: #0284c7; color: #ffffff; font-size: 0.8125rem; font-weight: 500;
      cursor: pointer; transition: all 0.15s ease;
    }
    .btn-reveal-action:hover:not(:disabled) { background: #0369a1; }
    .btn-reveal-action:disabled { opacity: 0.6; cursor: not-allowed; }

    .security-audit-notice {
      display: flex; gap: 0.75rem; padding: 0.875rem 1rem; border-radius: 0.5rem;
      background: #f8fafc; border: 1px solid #e2e8f0; color: #334155;
    }
    .security-audit-notice .notice-icon { color: #64748b; flex-shrink: 0; margin-top: 0.125rem; }
    .security-audit-notice h4 { font-size: 0.8125rem; font-weight: 600; margin: 0 0 0.25rem 0; color: #1e293b; }
    .security-audit-notice p { font-size: 0.75rem; color: #64748b; margin: 0; line-height: 1.4; }

    /* Social Sharing Section */
    .social-share-section {
      display: flex; flex-direction: column; gap: 0.75rem;
      background: #ffffff; border: 1px solid #e2e8f0; border-radius: 0.75rem;
      padding: 1rem; box-shadow: 0 1px 2px 0 rgba(15, 23, 42, 0.03);
    }
    .section-title {
      display: flex; align-items: center; gap: 0.375rem;
      font-size: 0.75rem; font-weight: 600; color: #64748b; text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .social-grid {
      display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.5rem;
    }
    @media (max-width: 480px) {
      .social-grid { grid-template-columns: repeat(3, 1fr); }
    }
    .social-btn {
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      gap: 0.375rem; padding: 0.625rem 0.25rem; border-radius: 0.5rem;
      border: 1px solid #e2e8f0; background: #ffffff; cursor: pointer;
      transition: all 0.15s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .social-btn:hover {
      transform: translateY(-1px);
      border-color: #cbd5e1;
      background: #f8fafc;
      box-shadow: 0 4px 6px -1px rgba(15, 23, 42, 0.05);
    }
    .social-icon {
      color: #475569;
      display: flex; align-items: center; justify-content: center;
      transition: color 0.15s ease;
    }
    .social-btn:hover .social-icon {
      color: #0f172a;
    }
    .social-name {
      font-size: 0.6875rem; font-weight: 500; color: #475569;
      transition: color 0.15s ease;
    }
    .social-btn:hover .social-name { color: #0f172a; }

    /* Cohesive QR code toggle active state */
    .social-btn.qrcode.active {
      border-color: #cbd5e1;
      background: #f1f5f9;
    }
    .social-btn.qrcode.active .social-icon {
      color: #0f172a;
    }
    .social-btn.qrcode.active .social-name {
      color: #0f172a; font-weight: 600;
    }

    .qr-code-card {
      margin-top: 0.5rem; padding: 1rem; border-radius: 0.625rem;
      background: #f8fafc; border: 1px solid #e2e8f0;
      display: flex; flex-direction: column; align-items: center; gap: 0.625rem;
      animation: fadeIn 0.18s ease-out;
    }
    .qr-code-header {
      width: 100%; display: flex; align-items: center; justify-content: space-between;
      font-size: 0.75rem; font-weight: 600; color: #0f172a;
    }
    .btn-close-qr {
      background: none; border: none; cursor: pointer; color: #64748b;
      padding: 0.25rem; border-radius: 0.25rem; display: flex; align-items: center;
      transition: all 0.15s ease;
    }
    .btn-close-qr:hover { background: #e2e8f0; color: #0f172a; }
    .qr-image-wrapper {
      padding: 0.5rem; background: #ffffff; border-radius: 0.375rem; border: 1px solid #e2e8f0;
      box-shadow: 0 1px 2px rgba(15,23,42,0.04);
    }
    .qr-image { width: 140px; height: 140px; display: block; border-radius: 0.25rem; }
    .qr-subtext { font-size: 0.6875rem; color: #64748b; text-align: center; }

    .meta-section-card {
      background: #ffffff; border: 1px solid #e2e8f0; border-radius: 0.75rem;
      padding: 1rem; display: flex; flex-direction: column; gap: 0.75rem;
      box-shadow: 0 1px 2px 0 rgba(15, 23, 42, 0.03);
    }

    .meta-grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem;
    }
    .meta-item { display: flex; flex-direction: column; gap: 0.1875rem; }
    .meta-label { font-size: 0.75rem; color: #64748b; font-weight: 500; }
    .meta-value { font-size: 0.8125rem; color: #0f172a; font-weight: 600; }
    .meta-value.highlight { color: #0f172a; font-weight: 600; }

    .modal-footer {
      padding: 1rem 1.5rem; border-top: 1px solid #e2e8f0; background: #f8fafc;
      display: flex; align-items: center; justify-content: space-between; gap: 0.75rem;
    }
    .btn-cancel {
      padding: 0.5rem 1rem; border-radius: 0.5rem; border: 1px solid #cbd5e1;
      background: #ffffff; color: #334155; font-size: 0.8125rem; font-weight: 500;
      cursor: pointer; transition: all 0.15s ease;
    }
    .btn-cancel:hover { background: #f1f5f9; color: #0f172a; }

    .btn-revoke-danger {
      display: flex; align-items: center; gap: 0.375rem;
      padding: 0.5rem 0.875rem; border-radius: 0.5rem; border: 1px solid #fecaca;
      background: #fef2f2; color: #991b1b; font-size: 0.8125rem; font-weight: 500;
      cursor: pointer; transition: all 0.15s ease;
    }
    .btn-revoke-danger:hover:not(:disabled) { background: #fee2e2; border-color: #fca5a5; }
    .btn-revoke-danger:disabled { opacity: 0.6; cursor: not-allowed; }

    .spinner-sm {
      width: 0.875rem; height: 0.875rem; border: 2px solid currentColor;
      border-top-color: transparent; border-radius: 50%;
      animation: spin 0.6s linear infinite; display: inline-block;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
  `]
})
export class ShareLinkDetailModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() shareLink: ShareLinkResponseDto | null = null;
  @Input() item: ShareItemTarget | null = null;

  @Output() closed = new EventEmitter<void>();
  @Output() revoked = new EventEmitter<void>();

  private readonly shareApiService = inject(ShareApiService);
  private readonly permissionApiService = inject(PermissionApiService);

  copied = signal<boolean>(false);
  showQrCode = signal<boolean>(false);
  isRevoking = signal<boolean>(false);
  isRevealing = signal<boolean>(false);
  showRevokeConfirm = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['shareLink']) {
      this.copied.set(false);
      this.showQrCode.set(false);
      this.showRevokeConfirm.set(false);
      this.isRevealing.set(false);
      this.errorMessage.set(null);
    }
  }

  onClose(): void {
    this.showRevokeConfirm.set(false);
    this.showQrCode.set(false);
    this.closed.emit();
  }

  getItemName(): string {
    return this.item?.data?.name || '';
  }

  getShareUrl(): string {
    if (!this.shareLink?.token) return '';
    return `${window.location.origin}/share/${this.shareLink.token}`;
  }

  getQrCodeUrl(): string {
    const url = this.getShareUrl();
    if (!url) return '';
    return `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(url)}`;
  }

  toggleQrCode(): void {
    this.showQrCode.update(v => !v);
  }

  copyUrl(): void {
    const url = this.getShareUrl();
    if (!url) return;
    if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
      navigator.clipboard.writeText(url).then(() => {
        this.copied.set(true);
        setTimeout(() => this.copied.set(false), 2500);
      }).catch(() => {});
    }
  }

  shareTo(platform: string): void {
    const shareUrl = this.getShareUrl();
    if (!shareUrl) return;
    const resourceName = this.getItemName() || 'Shared Resource';
    const text = `Check out this link: ${resourceName}`;

    let targetUrl = '';

    switch (platform) {
      case 'whatsapp':
        targetUrl = `https://api.whatsapp.com/send?text=${encodeURIComponent(text + '\n' + shareUrl)}`;
        break;
      case 'telegram':
        targetUrl = `https://t.me/share/url?url=${encodeURIComponent(shareUrl)}&text=${encodeURIComponent(text)}`;
        break;
      case 'twitter':
        targetUrl = `https://twitter.com/intent/tweet?url=${encodeURIComponent(shareUrl)}&text=${encodeURIComponent(text)}`;
        break;
      case 'linkedin':
        targetUrl = `https://www.linkedin.com/sharing/share-offsite/?url=${encodeURIComponent(shareUrl)}`;
        break;
      case 'facebook':
        targetUrl = `https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(shareUrl)}`;
        break;
      case 'email':
        targetUrl = `mailto:?subject=${encodeURIComponent('Shared Link: ' + resourceName)}&body=${encodeURIComponent(text + '\n\n' + shareUrl)}`;
        break;
    }

    if (targetUrl) {
      if (platform === 'email') {
        window.location.href = targetUrl;
      } else {
        window.open(targetUrl, '_blank', 'noopener,noreferrer');
      }
    }
  }

  formatDate(isoString?: string): string {
    if (!isoString) return 'N/A';
    const dt = new Date(isoString);
    if (isNaN(dt.getTime())) return isoString;
    return dt.toLocaleString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  formatRemainingTime(expiresAt?: string): string {
    if (!expiresAt) return 'No limit';
    const exp = new Date(expiresAt).getTime();
    const now = Date.now();
    const diffMs = exp - now;

    if (diffMs <= 0) return 'Expired';

    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    if (diffHours >= 48) {
      const days = Math.floor(diffHours / 24);
      return `${days} days remaining`;
    }
    if (diffHours >= 1) {
      return `${diffHours} hours remaining`;
    }
    const mins = Math.max(1, Math.floor(diffMs / (1000 * 60)));
    return `${mins} minutes remaining`;
  }

  promptRevokeShareLink(): void {
    this.showRevokeConfirm.set(true);
  }

  canReveal(): boolean {
    if (!this.shareLink) return false;
    if (this.shareLink.canReveal !== undefined && this.shareLink.canReveal !== null) {
      return !!this.shareLink.canReveal;
    }
    return !!this.shareLink.token;
  }

  revealAndCopy(): void {
    if (!this.shareLink) return;
    if (this.shareLink.token) {
      this.copyUrl();
      return;
    }
    const permId = this.shareLink.id;
    if (!permId) return;

    this.isRevealing.set(true);
    this.errorMessage.set(null);
    this.shareApiService.revealShareLink(permId).subscribe({
      next: (res) => {
        this.isRevealing.set(false);
        if (this.shareLink) {
          this.shareLink.token = res.shareLinkToken;
        }
        this.copyUrl();
      },
      error: (err) => {
        this.isRevealing.set(false);
        console.error('Failed to reveal share link:', err);
        this.errorMessage.set('Failed to reveal share link secret. You may not have permission to reveal this link.');
      }
    });
  }

  confirmRevokeShareLink(): void {
    if (!this.shareLink?.id) return;

    this.isRevoking.set(true);
    this.errorMessage.set(null);

    this.permissionApiService.revokePermission(this.shareLink.id).subscribe({
      next: () => {
        this.isRevoking.set(false);
        this.showRevokeConfirm.set(false);
        this.revoked.emit();
      },
      error: (err) => {
        console.error('Failed to revoke share link:', err);
        this.isRevoking.set(false);
        this.showRevokeConfirm.set(false);
        this.errorMessage.set('Failed to revoke share link. Please try again.');
      }
    });
  }
}
