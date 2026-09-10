import { Component, Input, Output, EventEmitter, inject, signal, computed, OnChanges, SimpleChanges, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccessEntryVM } from './item-detail-drawer.component';
import { FolderContentResponseDto } from '../../../core/models/folder.model';
import { PermissionApiService } from '../../permissions/services/permission-api.service';
import { PermissionLevel } from '../../../core/config/app.constants';
import { ConfirmationModalComponent } from '../../../shared/ui/modal/confirmation-modal.component';

@Component({
  selector: 'app-permission-detail-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmationModalComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen && permission) {
      <div class="modal-backdrop" (click)="onClose()">
        <div class="modal-card" (click)="$event.stopPropagation()" role="dialog" aria-modal="true" aria-labelledby="modal-perm-title">
          
          <!-- Header -->
          <div class="modal-header">
            <div class="modal-header-top">
              <div class="recipient-avatar-large" [ngClass]="getAvatarClass()">
                @if (permission.source === 'OWNER') {
                  <svg class="crown-icon" viewBox="0 0 14 10" fill="currentColor"><path d="M1 9h12M1.5 8L.5 2l3 2.5L7 1l3 3.5 3-2.5L12 8H2z"/></svg>
                } @else if (permission.entityType === 'TEAM') {
                  <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M11 14s1 0 1-1-1-4-5-4-5 3-5 4 1 1 1 1h8z"/><circle cx="7" cy="5" r="3"/><path d="M12 7a2 2 0 1 0 0-4 2 2 0 0 0 0 4z"/></svg>
                } @else if (permission.entityType === 'ORGANIZATION') {
                  <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M2 14h12M4 14V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v10M6 6h4M6 9h4"/></svg>
                } @else if (permission.entityType === 'COLLABORATION') {
                  <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M4.5 13.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zm7 0a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zm-3.5-6a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z"/></svg>
                } @else {
                  <span>{{ getInitials(permission.name || permission.email || 'U') }}</span>
                }
              </div>

              <div class="recipient-title-group">
                <div class="badges-row">
                  <span class="entity-badge" [ngClass]="permission.entityType?.toLowerCase()">
                    {{ permission.entityType }}
                  </span>
                  @if (permission.isExternal) {
                    <span class="external-badge">External</span>
                  }
                  <span class="source-badge" [ngClass]="getSourceClass()">
                    {{ getSourceLabel() }}
                  </span>
                </div>
                <h3 id="modal-perm-title" class="recipient-name">{{ permission.name }}</h3>
                <p class="recipient-subtext">{{ permission.subtext || permission.email || 'Workspace Member' }}</p>
              </div>

              <button type="button" class="btn-close-modal" (click)="onClose()" aria-label="Close detail modal">
                <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M4 4l8 8M12 4l-8 8"/></svg>
              </button>
            </div>
          </div>

          <!-- Body Scrollable Content -->
          <div class="modal-body">
            
            <!-- Alert Messages -->
            @if (errorMessage()) {
              <div class="alert-banner error">
                <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="8" cy="8" r="6"/><line x1="8" y1="5" x2="8" y2="9"/><circle cx="8" cy="11.5" r="0.75" fill="currentColor"/></svg>
                <span>{{ errorMessage() }}</span>
              </div>
            }
            @if (successMessage()) {
              <div class="alert-banner success">
                <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 8.5l3.5 3.5 6.5-7"/></svg>
                <span>{{ successMessage() }}</span>
              </div>
            }

            <!-- TIMESTAMPS & EXPIRATION STATUS CARD -->
            <div class="detail-section-card">
              <div class="section-card-title">
                <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="8" cy="8" r="6"/><polyline points="8 4 8 8 10.5 10.5"/></svg>
                <span>Timestamps & Expiration</span>
              </div>

              <div class="meta-grid">
                <div class="meta-item">
                  <span class="meta-label">Expiration Status</span>
                  <span class="status-pill" [ngClass]="getExpirationStatusClass()">
                    {{ getExpirationStatusText() }}
                  </span>
                </div>

                <div class="meta-item">
                  <span class="meta-label">Time Remaining</span>
                  <span class="meta-value highlight">{{ formatRemainingTime(permission.expiresAt) }}</span>
                </div>

                @if (permission.expiresAt) {
                  <div class="meta-item full-width">
                    <span class="meta-label">Exact Expiration Timestamp</span>
                    <span class="meta-value">{{ formatExactDate(permission.expiresAt) }}</span>
                  </div>
                }
              </div>
            </div>

            <!-- EDITABLE PERMISSION LEVEL & EXPIRATION FORM -->
            @if (permission.source !== 'OWNER' && permission.canRevoke !== false) {
              <div class="detail-section-card edit-card">
                <div class="section-card-title">
                  <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M11 2a2 2 0 0 1 2.83 2.83l-8.5 8.5-3.33.83.83-3.33L11 2z"/></svg>
                  <span>Edit Permission Settings</span>
                </div>

                <!-- Permission Level Switcher -->
                <div class="form-group">
                  <label class="form-label">Access Level</label>
                  <div class="level-selector-pills">
                    <button 
                      type="button" 
                      class="level-pill-btn"
                      [class.selected]="selectedLevel() === 'VIEW'"
                      (click)="selectedLevel.set('VIEW')"
                    >
                      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="8" cy="8" r="3"/><path d="M1 8s3-5.5 7-5.5S15 8 15 8s-3 5.5-7 5.5S1 8 1 8z"/></svg>
                      <span>View</span>
                    </button>
                    <button 
                      type="button" 
                      class="level-pill-btn"
                      [class.selected]="selectedLevel() === 'DOWNLOAD'"
                      (click)="selectedLevel.set('DOWNLOAD')"
                    >
                      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M8 2v9M4 8l4 4 4-4M2 14h12"/></svg>
                      <span>Download</span>
                    </button>
                    <button 
                      type="button" 
                      class="level-pill-btn"
                      [class.selected]="selectedLevel() === 'EDIT'"
                      (click)="selectedLevel.set('EDIT')"
                    >
                      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M11 2a2 2 0 0 1 2.83 2.83l-8.5 8.5-3.33.83.83-3.33L11 2z"/></svg>
                      <span>Edit</span>
                    </button>
                  </div>
                </div>

                <!-- Expiration Control -->
                <div class="form-group">
                  <label class="form-label">Expiration Setting</label>
                  <div class="expiration-presets">
                    <button 
                      type="button" 
                      class="preset-btn"
                      [class.active]="activePreset() === 'none'"
                      (click)="setPreset('none')"
                    >
                      Never
                    </button>
                    <button 
                      type="button" 
                      class="preset-btn"
                      [class.active]="activePreset() === '1h'"
                      (click)="setPreset('1h')"
                    >
                      1 Hour
                    </button>
                    <button 
                      type="button" 
                      class="preset-btn"
                      [class.active]="activePreset() === '24h'"
                      (click)="setPreset('24h')"
                    >
                      24 Hours
                    </button>
                    <button 
                      type="button" 
                      class="preset-btn"
                      [class.active]="activePreset() === '7d'"
                      (click)="setPreset('7d')"
                    >
                      7 Days
                    </button>
                    <button 
                      type="button" 
                      class="preset-btn"
                      [class.active]="activePreset() === '30d'"
                      (click)="setPreset('30d')"
                    >
                      30 Days
                    </button>
                    <button 
                      type="button" 
                      class="preset-btn"
                      [class.active]="activePreset() === 'custom'"
                      (click)="activePreset.set('custom')"
                    >
                      Custom
                    </button>
                  </div>

                  @if (activePreset() === 'custom') {
                    <div class="custom-date-input-wrap">
                      <input 
                        type="datetime-local" 
                        class="datetime-input"
                        [value]="customDateValue()"
                        (input)="customDateValue.set($any($event.target).value)"
                      />
                    </div>
                  }
                </div>

                <div class="form-actions">
                  <button 
                    type="button" 
                    class="btn-save-changes"
                    [disabled]="isSaving()"
                    (click)="saveChanges()"
                  >
                    @if (isSaving()) {
                      <span class="spinner-sm"></span>
                      <span>Saving...</span>
                    } @else {
                      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 8.5l3.5 3.5 6.5-7"/></svg>
                      <span>Save Permission Changes</span>
                    }
                  </button>
                </div>
              </div>
            }

            <!-- FOLDER CONTENTS & EXCLUSIONS SECTION (if item is a folder) -->
            @if (item?.type === 'folder') {
              <div class="detail-section-card">
                <div class="section-card-title">
                  <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M2 4a1 1 0 0 1 1-1h4l2 2h4a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4z"/></svg>
                  <span>Folder Items Scope</span>
                </div>

                <p class="section-desc">
                  This permission applies to the folder contents below, respecting configured child exclusions.
                </p>

                @if (!folderContents || (!folderContents.subFolders?.length && !folderContents.documents?.length)) {
                  <div class="empty-folder-scope">
                    <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M2 4a1 1 0 0 1 1-1h4l2 2h4a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4z"/></svg>
                    <span>Folder is empty or contents are loading.</span>
                  </div>
                } @else {
                  <div class="folder-items-list">
                    @for (sf of folderContents.subFolders; track sf.id) {
                      <div class="folder-item-row">
                        <div class="item-icon folder-icon">
                          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M2 4a1 1 0 0 1 1-1h4l2 2h4a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4z"/></svg>
                        </div>
                        <span class="item-name">{{ sf.name }}</span>
                        <span class="scope-pill" [class.excluded]="isSubfolderExcluded(sf.id)">
                          {{ isSubfolderExcluded(sf.id) ? 'Excluded' : 'Included' }}
                        </span>
                      </div>
                    }
                    @for (doc of folderContents.documents; track doc.id) {
                      <div class="folder-item-row">
                        <div class="item-icon doc-icon">
                          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9 2H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1V6L9 2z"/><polyline points="9 2 9 6 13 6"/></svg>
                        </div>
                        <span class="item-name">{{ doc.name || doc.fileName || 'Untitled File' }}</span>
                        <span class="scope-pill" [class.excluded]="isDocumentExcluded(+doc.id)">
                          {{ isDocumentExcluded(+doc.id) ? 'Excluded' : 'Included' }}
                        </span>
                      </div>
                    }
                  </div>
                }
              </div>
            }

          </div>

          <!-- Footer Actions -->
          <div class="modal-footer">
            @if (permission.source !== 'OWNER' && permission.canRevoke !== false && permission.permissionId) {
              <button 
                type="button" 
                class="btn-revoke-danger"
                [disabled]="isRevoking()"
                (click)="promptRevokeAccess()"
              >
                @if (isRevoking()) {
                  <span class="spinner-sm"></span>
                  <span>Revoking...</span>
                } @else {
                  <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><line x1="4" y1="4" x2="12" y2="12"/><line x1="12" y1="4" x2="4" y2="12"/></svg>
                  <span>Revoke Permission</span>
                }
              </button>
            }

            <button type="button" class="btn-cancel" (click)="onClose()">
              Close
            </button>
          </div>

        </div>
      </div>
    }

    <!-- REVOKE PERMISSION CONFIRMATION MODAL -->
    <sd-confirmation-modal
      [open]="showRevokeConfirm()"
      title="Revoke Permission"
      [message]="'Are you sure you want to revoke access for ' + (permission?.name || 'this recipient') + '? They will lose all access rights immediately.'"
      confirmText="Revoke Access"
      cancelText="Cancel"
      variant="danger"
      [loading]="isRevoking()"
      (confirmed)="confirmRevokeAccess()"
      (cancelled)="showRevokeConfirm.set(false)"
    />
  `,
  styles: [`
    .modal-backdrop {
      position: fixed; inset: 0;
      background: rgba(15, 23, 42, 0.45);
      backdrop-filter: blur(6px);
      z-index: var(--sd-z-drawer-backdrop, 2200);
      display: flex; align-items: center; justify-content: center;
      padding: 1.25rem;
      animation: fadeIn 0.15s ease-out;
    }
    @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

    .modal-card {
      width: 100%; max-width: 500px;
      max-height: 90vh;
      background: #ffffff;
      border-radius: 0.875rem;
      box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1), 0 8px 10px -6px rgba(0,0,0,0.1);
      display: flex; flex-direction: column;
      overflow: hidden;
      border: 1px solid #e2e8f0;
      animation: scaleUp 0.18s cubic-bezier(0.16, 1, 0.3, 1);
    }
    @keyframes scaleUp {
      from { transform: scale(0.95); opacity: 0; }
      to   { transform: scale(1); opacity: 1; }
    }

    .modal-header {
      padding: 1.25rem 1.5rem 1rem;
      border-bottom: 1px solid #f1f5f9;
      background: #f8fafc;
    }
    .modal-header-top {
      display: flex; align-items: flex-start; gap: 0.875rem;
    }
    .recipient-avatar-large {
      width: 3rem; height: 3rem;
      border-radius: 0.75rem;
      display: flex; align-items: center; justify-content: center;
      font-weight: 700; font-size: 1.125rem;
      flex-shrink: 0;
      background: #e2e8f0; color: #334155;
    }
    .recipient-avatar-large.team { background: #e0f2fe; color: #0284c7; }
    .recipient-avatar-large.organization { background: #f0fdf4; color: #16a34a; }
    .recipient-avatar-large.collaboration { background: #fef3c7; color: #d97706; }
    .recipient-avatar-large.user { background: #f1f5f9; color: #475569; }
    .recipient-avatar-large.owner { background: #fef3c7; color: #b45309; }
    .crown-icon { width: 1.25rem; height: 1.25rem; }

    .recipient-title-group {
      flex: 1; min-width: 0;
      display: flex; flex-direction: column; gap: 0.25rem;
    }
    .badges-row {
      display: flex; align-items: center; gap: 0.375rem; flex-wrap: wrap;
    }
    .entity-badge {
      font-size: 0.6875rem; font-weight: 700; text-transform: uppercase;
      letter-spacing: 0.03em; padding: 0.125rem 0.4375rem; border-radius: 0.375rem;
      background: #f1f5f9; color: #475569;
    }
    .entity-badge.team { background: #e0f2fe; color: #0369a1; }
    .entity-badge.organization { background: #dcfce7; color: #15803d; }
    .entity-badge.collaboration { background: #fef3c7; color: #b45309; }

    .external-badge {
      font-size: 0.6875rem; font-weight: 600; padding: 0.125rem 0.4375rem;
      border-radius: 0.375rem; background: #fff7ed; color: #c2410c; border: 1px solid #ffedd5;
    }
    .source-badge {
      font-size: 0.6875rem; font-weight: 600; padding: 0.125rem 0.4375rem;
      border-radius: 0.375rem; background: #f8fafc; color: #64748b; border: 1px solid #e2e8f0;
    }
    .source-badge.inherited { background: #eff6ff; color: #1d4ed8; border-color: #dbeafe; }
    .source-badge.owner { background: #fffbeb; color: #b45309; border-color: #fef3c7; }

    .recipient-name {
      font-size: 1.125rem; font-weight: 700; color: #0f172a; margin: 0;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .recipient-subtext {
      font-size: 0.8125rem; color: #64748b; margin: 0;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }

    .btn-close-modal {
      background: transparent; border: none; cursor: pointer; color: #94a3b8;
      padding: 0.375rem; border-radius: 0.375rem; display: flex; align-items: center;
      transition: all 0.15s ease;
    }
    .btn-close-modal:hover { background: #e2e8f0; color: #0f172a; }
    .btn-close-modal svg { width: 1.125rem; height: 1.125rem; }

    /* Modal Body */
    .modal-body {
      padding: 1.25rem 1.5rem;
      overflow-y: auto;
      display: flex; flex-direction: column; gap: 1rem;
    }

    .alert-banner {
      display: flex; align-items: center; gap: 0.5rem;
      padding: 0.625rem 0.875rem; border-radius: 0.5rem; font-size: 0.8125rem; font-weight: 500;
    }
    .alert-banner svg { width: 1rem; height: 1rem; flex-shrink: 0; }
    .alert-banner.error { background: #fef2f2; color: #991b1b; border: 1px solid #fee2e2; }
    .alert-banner.success { background: #f0fdf4; color: #166534; border: 1px solid #dcfce7; }

    .detail-section-card {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 0.625rem;
      padding: 1rem;
      display: flex; flex-direction: column; gap: 0.75rem;
    }
    .detail-section-card.edit-card {
      background: #ffffff;
      border-color: #cbd5e1;
    }

    .section-card-title {
      display: flex; align-items: center; gap: 0.5rem;
      font-size: 0.8125rem; font-weight: 700; color: #334155; text-transform: uppercase;
      letter-spacing: 0.02em;
    }
    .section-card-title svg { width: 1rem; height: 1rem; color: #64748b; }

    .meta-grid {
      display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem;
    }
    .meta-item { display: flex; flex-direction: column; gap: 0.25rem; }
    .meta-item.full-width { grid-column: 1 / -1; }
    .meta-label { font-size: 0.75rem; color: #64748b; font-weight: 500; }
    .meta-value { font-size: 0.875rem; color: #0f172a; font-weight: 600; }
    .meta-value.highlight { color: #0284c7; }

    .status-pill {
      display: inline-flex; align-items: center; padding: 0.1875rem 0.5rem;
      border-radius: 9999px; font-size: 0.75rem; font-weight: 700; width: fit-content;
    }
    .status-pill.active { background: #dcfce7; color: #15803d; }
    .status-pill.expiring { background: #fff7ed; color: #c2410c; }
    .status-pill.expired { background: #fee2e2; color: #b91c1c; }

    /* Form controls */
    .form-group { display: flex; flex-direction: column; gap: 0.375rem; }
    .form-label { font-size: 0.8125rem; font-weight: 600; color: #334155; }

    .level-selector-pills {
      display: flex; gap: 0.5rem;
    }
    .level-pill-btn {
      flex: 1; display: flex; align-items: center; justify-content: center; gap: 0.375rem;
      padding: 0.5rem 0.75rem; border-radius: 0.5rem; border: 1px solid #cbd5e1;
      background: #ffffff; color: #475569; font-size: 0.8125rem; font-weight: 600;
      cursor: pointer; transition: all 0.15s ease;
    }
    .level-pill-btn svg { width: 0.875rem; height: 0.875rem; }
    .level-pill-btn:hover { background: #f8fafc; border-color: #94a3b8; }
    .level-pill-btn.selected {
      background: #0f172a; color: #ffffff; border-color: #0f172a;
    }

    .expiration-presets {
      display: flex; flex-wrap: wrap; gap: 0.375rem;
    }
    .preset-btn {
      padding: 0.375rem 0.625rem; border-radius: 0.375rem; border: 1px solid #e2e8f0;
      background: #f8fafc; color: #475569; font-size: 0.75rem; font-weight: 600;
      cursor: pointer; transition: all 0.15s ease;
    }
    .preset-btn:hover { background: #f1f5f9; color: #0f172a; }
    .preset-btn.active {
      background: #0284c7; color: #ffffff; border-color: #0284c7;
    }

    .custom-date-input-wrap { margin-top: 0.375rem; }
    .datetime-input {
      width: 100%; padding: 0.5rem 0.75rem; border-radius: 0.5rem;
      border: 1px solid #cbd5e1; font-size: 0.8125rem; color: #0f172a; outline: none;
    }
    .datetime-input:focus { border-color: #0284c7; box-shadow: 0 0 0 2px rgba(2,132,199,0.15); }

    .form-actions { display: flex; justify-content: flex-end; margin-top: 0.375rem; }
    .btn-save-changes {
      display: flex; align-items: center; gap: 0.375rem;
      padding: 0.5rem 1rem; border-radius: 0.5rem; border: none;
      background: #0f172a; color: #ffffff; font-size: 0.8125rem; font-weight: 600;
      cursor: pointer; transition: all 0.15s ease;
    }
    .btn-save-changes svg { width: 0.875rem; height: 0.875rem; }
    .btn-save-changes:hover:not(:disabled) { background: #1e293b; }
    .btn-save-changes:disabled { opacity: 0.6; cursor: not-allowed; }

    /* Folder Scope */
    .section-desc { font-size: 0.75rem; color: #64748b; margin: 0; }
    .empty-folder-scope {
      display: flex; align-items: center; gap: 0.5rem; font-size: 0.8125rem; color: #94a3b8;
      padding: 0.5rem 0;
    }
    .empty-folder-scope svg { width: 1rem; height: 1rem; }
    .folder-items-list {
      display: flex; flex-direction: column; gap: 0.375rem; max-height: 160px; overflow-y: auto;
    }
    .folder-item-row {
      display: flex; align-items: center; gap: 0.5rem; padding: 0.375rem 0.5rem;
      border-radius: 0.375rem; background: #ffffff; border: 1px solid #e2e8f0; font-size: 0.8125rem;
    }
    .item-icon { width: 1.25rem; height: 1.25rem; display: flex; align-items: center; justify-content: center; }
    .item-icon svg { width: 0.875rem; height: 0.875rem; }
    .folder-icon { color: #0284c7; }
    .doc-icon { color: #64748b; }
    .item-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #334155; font-weight: 500; }
    .scope-pill {
      font-size: 0.6875rem; font-weight: 600; padding: 0.125rem 0.375rem; border-radius: 0.25rem;
      background: #dcfce7; color: #15803d;
    }
    .scope-pill.excluded { background: #fee2e2; color: #b91c1c; }

    /* Footer */
    .modal-footer {
      padding: 0.875rem 1.5rem; border-top: 1px solid #f1f5f9; background: #f8fafc;
      display: flex; align-items: center; justify-content: space-between; gap: 0.75rem;
    }
    .btn-cancel {
      padding: 0.5rem 1rem; border-radius: 0.5rem; border: 1px solid #cbd5e1;
      background: #ffffff; color: #475569; font-size: 0.8125rem; font-weight: 600;
      cursor: pointer; transition: all 0.15s ease;
    }
    .btn-cancel:hover { background: #f1f5f9; color: #0f172a; }

    .btn-revoke-danger {
      display: flex; align-items: center; gap: 0.375rem;
      padding: 0.5rem 0.875rem; border-radius: 0.5rem; border: 1px solid #fca5a5;
      background: #fef2f2; color: #991b1b; font-size: 0.8125rem; font-weight: 600;
      cursor: pointer; transition: all 0.15s ease;
    }
    .btn-revoke-danger svg { width: 0.875rem; height: 0.875rem; }
    .btn-revoke-danger:hover:not(:disabled) { background: #fee2e2; border-color: #f87171; }
    .btn-revoke-danger:disabled { opacity: 0.6; cursor: not-allowed; }

    .spinner-sm {
      width: 0.875rem; height: 0.875rem; border: 2px solid currentColor;
      border-top-color: transparent; border-radius: 50%;
      animation: spin 0.6s linear infinite; display: inline-block;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
  `]
})
export class PermissionDetailModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() permission: AccessEntryVM | null = null;
  @Input() item: { type: 'folder' | 'document'; data: any; tab?: 'overview' | 'access' } | null = null;
  @Input() folderContents: FolderContentResponseDto | null = null;

  @Output() closed = new EventEmitter<void>();
  @Output() updated = new EventEmitter<void>();
  @Output() revoked = new EventEmitter<void>();

  private readonly permissionApiService = inject(PermissionApiService);

  selectedLevel = signal<string>('VIEW');
  activePreset = signal<'none' | '1h' | '24h' | '7d' | '30d' | 'custom'>('none');
  customDateValue = signal<string>('');

  isSaving = signal<boolean>(false);
  isRevoking = signal<boolean>(false);
  showRevokeConfirm = signal<boolean>(false);

  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['permission'] && this.permission) {
      this.selectedLevel.set(this.permission.level || 'VIEW');
      this.errorMessage.set(null);
      this.successMessage.set(null);

      if (this.permission.expiresAt) {
        this.activePreset.set('custom');
        const dt = new Date(this.permission.expiresAt);
        if (!isNaN(dt.getTime())) {
          this.customDateValue.set(dt.toISOString().slice(0, 16));
        } else {
          this.customDateValue.set('');
        }
      } else {
        this.activePreset.set('none');
        this.customDateValue.set('');
      }
    }
  }

  onClose(): void {
    this.closed.emit();
  }

  getInitials(name: string): string {
    if (!name) return 'U';
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  }

  getAvatarClass(): string {
    if (!this.permission) return 'user';
    if (this.permission.source === 'OWNER') return 'owner';
    return (this.permission.entityType || 'user').toLowerCase();
  }

  getSourceLabel(): string {
    if (!this.permission) return 'Direct';
    switch (this.permission.source) {
      case 'OWNER': return 'Resource Owner';
      case 'INHERITED': return `Inherited from ${this.permission.inheritedFrom || 'Parent Folder'}`;
      case 'ORGANIZATION': return 'Organization Policy';
      case 'TEAM': return 'Team Permission';
      case 'WORKSPACE': return 'Workspace Default';
      case 'COLLABORATION': return 'Collaboration Group';
      default: return 'Direct Permission';
    }
  }

  getSourceClass(): string {
    if (!this.permission) return '';
    if (this.permission.source === 'INHERITED') return 'inherited';
    if (this.permission.source === 'OWNER') return 'owner';
    return '';
  }

  getExpirationStatusText(): string {
    if (!this.permission?.expiresAt) return 'Permanent (No Expiration)';
    const remaining = this.formatRemainingTime(this.permission.expiresAt);
    if (remaining === 'Expired') return 'Expired';
    return 'Active';
  }

  getExpirationStatusClass(): string {
    if (!this.permission?.expiresAt) return 'active';
    const remaining = this.formatRemainingTime(this.permission.expiresAt);
    if (remaining === 'Expired') return 'expired';
    return 'active';
  }

  formatExactDate(isoString?: string): string {
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

  setPreset(preset: 'none' | '1h' | '24h' | '7d' | '30d'): void {
    this.activePreset.set(preset);
  }

  computeExpiresAtIso(): string | undefined {
    const preset = this.activePreset();
    if (preset === 'none') return undefined;

    const now = new Date();
    if (preset === '1h') {
      return new Date(now.getTime() + 60 * 60 * 1000).toISOString();
    }
    if (preset === '24h') {
      return new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString();
    }
    if (preset === '7d') {
      return new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString();
    }
    if (preset === '30d') {
      return new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString();
    }
    if (preset === 'custom' && this.customDateValue()) {
      return new Date(this.customDateValue()).toISOString();
    }
    return undefined;
  }

  saveChanges(): void {
    if (!this.permission?.permissionId) return;

    this.isSaving.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    const level = this.selectedLevel() as PermissionLevel;
    const expiresAt = this.computeExpiresAtIso();

    this.permissionApiService.updatePermission(this.permission.permissionId, {
      permissionLevel: level,
      expiresAt: expiresAt
    }).subscribe({
      next: () => {
        this.isSaving.set(false);
        this.successMessage.set('Permission updated successfully!');
        setTimeout(() => {
          this.updated.emit();
        }, 800);
      },
      error: (err) => {
        console.error('Failed to update permission:', err);
        this.isSaving.set(false);
        this.errorMessage.set('Failed to update permission settings.');
      }
    });
  }

  promptRevokeAccess(): void {
    if (!this.permission?.permissionId) return;
    this.showRevokeConfirm.set(true);
  }

  confirmRevokeAccess(): void {
    if (!this.permission?.permissionId) return;

    this.isRevoking.set(true);
    this.errorMessage.set(null);

    this.permissionApiService.revokePermission(this.permission.permissionId).subscribe({
      next: () => {
        this.isRevoking.set(false);
        this.showRevokeConfirm.set(false);
        this.revoked.emit();
      },
      error: (err) => {
        console.error('Failed to revoke permission:', err);
        this.isRevoking.set(false);
        this.showRevokeConfirm.set(false);
        this.errorMessage.set('Failed to revoke permission.');
      }
    });
  }

  isSubfolderExcluded(subfolderId: number): boolean {
    return false; // Dynamic checking from item if needed
  }

  isDocumentExcluded(docId: number): boolean {
    return false;
  }
}
