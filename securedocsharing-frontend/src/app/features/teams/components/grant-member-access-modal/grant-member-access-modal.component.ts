import {
  Component,
  ChangeDetectionStrategy,
  Input,
  Output,
  EventEmitter,
  inject,
  signal,
  computed,
  HostListener,
  OnChanges,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import {
  LucideShieldCheck,
  LucideX,
  LucideFolder,
  LucideFileText,
  LucideUser,
  LucideAlertCircle,
  LucideSearch,
  LucideLock,
  LucideArrowLeft,
} from '@lucide/angular';
import { TeamDelegationService } from '../../services/team-delegation.service';
import {
  TeamAccessBoundaryDto,
  TeamMemberAccessDto,
  TeamPermissionLevel,
  ScopedResourceNodeDto,
} from '../../models/team-delegation.models';
import { OrganizationMemberResponseDto } from '../../../../core/models/organization.model';

interface FlatResourceOption {
  id: number;
  name: string;
  type: 'FOLDER' | 'DOCUMENT';
  path: string;
}

@Component({
  selector: 'sd-grant-member-access-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LucideShieldCheck,
    LucideX,
    LucideFolder,
    LucideFileText,
    LucideUser,
    LucideAlertCircle,
    LucideSearch,
    LucideLock,
    LucideArrowLeft,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen) {
      @if (inline) {
        <!-- In-Sheet Embedded Workflow (No Modal Backdrop, No Stacking) -->
        <div class="grant-inline-view">
          <header class="inline-header">
            <button
              type="button"
              class="btn-inline-back"
              (click)="onClose()"
              title="Return to team details"
            >
              <svg lucideArrowLeft class="icon-back"></svg>
              <span>Back to Team Details</span>
            </button>
            <div class="inline-title-group">
              <h3 class="inline-title">{{ isEditMode() ? 'Edit Member Access' : 'Grant Member Access' }}</h3>
              <span class="inline-subtitle">
                {{ isEditMode() ? 'Update permission level for this member within the boundary.' : 'Charter individual member access to folders or documents inside the team boundary.' }}
              </span>
            </div>
          </header>

          <div class="inline-body">
            <ng-container *ngTemplateOutlet="formContentTemplate"></ng-container>
          </div>

          <footer class="inline-footer">
            <button
              type="button"
              class="btn btn-secondary"
              (click)="onClose()"
              [disabled]="isLoading()"
            >
              Cancel
            </button>
            <button
              type="button"
              class="btn btn-primary"
              (click)="onSave()"
              [disabled]="isLoading() || !isValid()"
            >
              @if (isLoading()) {
                <span class="spinner"></span>
                <span>Saving...</span>
              } @else {
                <span>{{ isEditMode() ? 'Update Permission' : 'Charter Access' }}</span>
              }
            </button>
          </footer>
        </div>
      } @else {
        <!-- Standard Modal Overlay (When used standalone) -->
        <div class="modal-backdrop" (click)="onClose()">
          <div
            class="modal-card"
            (click)="$event.stopPropagation()"
            role="dialog"
            aria-labelledby="grant-modal-title"
            aria-modal="true"
          >
            <!-- Header -->
            <div class="modal-header">
              <div class="modal-header-left">
                <div class="icon-badge">
                  <svg lucideShieldCheck class="header-lucide-icon"></svg>
                </div>
                <div>
                  <h2 id="grant-modal-title" class="modal-title">
                    {{ isEditMode() ? 'Edit Member Access' : 'Grant Member Access' }}
                  </h2>
                  <p class="modal-subtitle">
                    {{ isEditMode() ? 'Update permission level for this member within the boundary.' : 'Charter individual member access to folders or documents inside the team boundary.' }}
                  </p>
                </div>
              </div>
              <button
                type="button"
                class="btn-close"
                (click)="onClose()"
                title="Close modal (Esc)"
                aria-label="Close modal"
              >
                <svg lucideX class="close-icon"></svg>
              </button>
            </div>

            <!-- Body -->
            <div class="modal-body">
              <ng-container *ngTemplateOutlet="formContentTemplate"></ng-container>
            </div>

            <!-- Footer -->
            <div class="modal-footer">
              <button
                type="button"
                class="btn btn-secondary"
                (click)="onClose()"
                [disabled]="isLoading()"
              >
                Cancel
              </button>
              <button
                type="button"
                class="btn btn-primary"
                (click)="onSave()"
                [disabled]="isLoading() || !isValid()"
              >
                @if (isLoading()) {
                  <span class="spinner"></span>
                  <span>Saving...</span>
                } @else {
                  <span>{{ isEditMode() ? 'Update Permission' : 'Charter Access' }}</span>
                }
              </button>
            </div>
          </div>
        </div>
      }

      <!-- Reusable Form Template -->
      <ng-template #formContentTemplate>
        @if (errorMessage()) {
          <div class="alert-banner error-banner">
            <svg lucideAlertCircle class="banner-icon"></svg>
            <span>{{ errorMessage() }}</span>
          </div>
        }

        <!-- Boundary Specification Callout -->
        @if (boundary) {
          <div class="boundary-callout">
            <div class="boundary-icon-box">
              <svg lucideFolder class="boundary-icon"></svg>
            </div>
            <div class="boundary-info">
              <span class="boundary-label">Governing Boundary Root</span>
              <span class="boundary-value">{{ boundary.rootFolderName }}</span>
            </div>
            <span class="boundary-ceiling-pill">
              Ceiling: {{ boundary.permissionLevel }}
            </span>
          </div>
        }

        <!-- 1. Member Selection -->
        <div class="form-group">
          <label class="form-label" for="member-select">Team Member <span class="required">*</span></label>
          @if (isEditMode()) {
            <div class="readonly-input-box">
              <svg lucideUser class="input-icon"></svg>
              <span class="font-medium">{{ editingAccess?.userName || editingAccess?.userEmail }}</span>
              <span class="sub-text">({{ editingAccess?.userEmail }})</span>
            </div>
          } @else {
            <div class="select-wrapper">
              <select
                id="member-select"
                class="form-select"
                [ngModel]="selectedEmail()"
                (ngModelChange)="selectedEmail.set($event)"
                [disabled]="isLoading()"
              >
                <option value="" disabled>Select a team member...</option>
                @for (m of availableMembers; track m.email) {
                  <option [value]="m.email">
                    {{ m.firstName ? (m.firstName + ' ' + (m.lastName || '')) : m.email }} ({{ m.email }})
                  </option>
                }
              </select>
            </div>
          }
        </div>

        <!-- 2. Target Resource Selection (Scoped Resource Picker) -->
        <div class="form-group">
          <label class="form-label">Target Resource (Folder or File) <span class="required">*</span></label>
          @if (isEditMode()) {
            <div class="readonly-input-box">
              @if (editingAccess?.resourceType === 'FOLDER') {
                <svg lucideFolder class="input-icon"></svg>
              } @else {
                <svg lucideFileText class="input-icon"></svg>
              }
              <span class="font-medium">{{ editingAccess?.resourceName }}</span>
              <span class="sub-text font-mono">({{ editingAccess?.resourcePath || 'Root' }})</span>
            </div>
          } @else {
            <div class="resource-picker-container">
              <!-- Boundary-Scoped Search Bar -->
              <div class="resource-search-box">
                <svg lucideSearch class="search-icon"></svg>
                <input
                  type="text"
                  class="search-input"
                  placeholder="Filter resources inside boundary..."
                  [ngModel]="resourceSearchQuery()"
                  (ngModelChange)="resourceSearchQuery.set($event)"
                  [disabled]="isLoading() || isLoadingResources()"
                />
                @if (resourceSearchQuery()) {
                  <button type="button" class="btn-clear-search" (click)="resourceSearchQuery.set('')">
                    <svg lucideX class="clear-icon"></svg>
                  </button>
                }
              </div>

              <!-- Resource Loading State -->
              @if (isLoadingResources()) {
                <div class="resource-loading">
                  <span class="spinner-sm"></span>
                  <span>Discovering boundary resources...</span>
                </div>
              } @else if (filteredResourceOptions().length === 0) {
                <div class="resource-empty">
                  <span>No resources found matching "{{ resourceSearchQuery() }}"</span>
                </div>
              } @else {
                <!-- Scoped Resources List -->
                <div class="resource-list-scroll">
                  @for (res of filteredResourceOptions(); track res.type + ':' + res.id) {
                    <div
                      class="resource-option-row"
                      [class.selected]="selectedResourceKey() === (res.type + ':' + res.id)"
                      (click)="onResourceSelected(res.type + ':' + res.id)"
                    >
                      <div class="option-radio">
                        <div class="radio-dot" [class.active]="selectedResourceKey() === (res.type + ':' + res.id)"></div>
                      </div>
                      <div class="option-icon-box" [class.is-doc]="res.type === 'DOCUMENT'">
                        @if (res.type === 'DOCUMENT') {
                          <svg lucideFileText class="option-icon"></svg>
                        } @else {
                          <svg lucideFolder class="option-icon"></svg>
                        }
                      </div>
                      <div class="option-text">
                        <span class="option-name">{{ res.name }}</span>
                        <span class="option-path font-mono">{{ res.path }}</span>
                      </div>
                      <span class="option-type-badge">{{ res.type }}</span>
                    </div>
                  }
                </div>
              }
            </div>
          }
        </div>

        <!-- 3. Permission Level Selection with Ceiling Clamping -->
        <div class="form-group">
          <label class="form-label">Permission Level <span class="required">*</span></label>
          <div class="permission-options-grid">
            @for (lvl of allPermissionLevels; track lvl) {
              @let isAllowed = allowedLevels().includes(lvl);
              <div
                class="level-card"
                [class.selected]="selectedLevel() === lvl && isAllowed"
                [class.disabled]="!isAllowed"
                (click)="isAllowed && selectedLevel.set(lvl)"
              >
                <div class="level-radio">
                  <div class="radio-inner" [class.active]="selectedLevel() === lvl && isAllowed"></div>
                </div>
                <div class="level-meta">
                  <div class="level-title-row">
                    <span class="level-name" [class.is-edit]="lvl === 'EDIT'" [class.is-download]="lvl === 'DOWNLOAD'">
                      {{ lvl }}
                    </span>
                    @if (!isAllowed) {
                      <span class="ceiling-exceeded-badge">
                        <svg lucideLock class="lock-icon"></svg>
                        Exceeds Ceiling
                      </span>
                    }
                  </div>
                  <span class="level-desc">
                    {{ getLevelDescription(lvl) }}
                  </span>
                </div>
              </div>
            }
          </div>
        </div>

        <!-- 4. Optional Delegation Note -->
        <div class="form-group">
          <label class="form-label" for="grant-note">Delegation Note (Optional)</label>
          <input
            id="grant-note"
            type="text"
            class="form-input"
            placeholder="e.g. Lead reviewer delegation for Q1 audit"
            [ngModel]="note()"
            (ngModelChange)="note.set($event)"
            [disabled]="isLoading()"
            maxlength="255"
          />
        </div>
      </ng-template>
    }
  `,
  styles: [
    `
      /* ── Inline Sheet View Styles ───────────────── */
      .grant-inline-view {
        display: flex;
        flex-direction: column;
        height: 100%;
        background: #ffffff;
      }

      .inline-header {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        padding: 1.25rem 1.75rem;
        border-bottom: 1px solid #e2e8f0;
        background: #f8fafc;
      }

      .btn-inline-back {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        font-size: 0.8125rem;
        font-weight: 500;
        color: #ea580c;
        background: transparent;
        border: none;
        padding: 0;
        cursor: pointer;
        transition: color 0.15s ease;

        &:hover {
          color: #c2410c;
          text-decoration: underline;
        }
      }

      .icon-back {
        width: 15px;
        height: 15px;
      }

      .inline-title-group {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
      }

      .inline-title {
        margin: 0;
        font-size: 1.125rem;
        font-weight: 600;
        color: #0f172a;
      }

      .inline-subtitle {
        font-size: 0.8125rem;
        color: #64748b;
      }

      .inline-body {
        flex: 1;
        overflow-y: auto;
        padding: 1.5rem 1.75rem;
        display: flex;
        flex-direction: column;
        gap: 1.25rem;
      }

      .inline-footer {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 0.75rem;
        padding: 1.125rem 1.75rem;
        border-top: 1px solid #e2e8f0;
        background: #f8fafc;
      }

      /* ── Modal Overlay Styles (Standalone) ──────── */
      .modal-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(9, 9, 11, 0.4);
        backdrop-filter: blur(4px);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1050;
        padding: 1rem;
        animation: fadeIn 0.15s ease-out;
      }

      .modal-card {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
        width: 100%;
        max-width: 560px;
        max-height: 90vh;
        display: flex;
        flex-direction: column;
        animation: scaleIn 0.2s cubic-bezier(0.16, 1, 0.3, 1);
        overflow: hidden;
      }

      @keyframes fadeIn {
        from { opacity: 0; }
        to { opacity: 1; }
      }

      @keyframes scaleIn {
        from { opacity: 0; transform: scale(0.97); }
        to { opacity: 1; transform: scale(1); }
      }

      .modal-header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        padding: 1.25rem 1.5rem;
        border-bottom: 1px solid #e2e8f0;
      }

      .modal-header-left {
        display: flex;
        align-items: flex-start;
        gap: 0.875rem;
      }

      .icon-badge {
        width: 40px;
        height: 40px;
        border-radius: 10px;
        background: #fff7ed;
        color: #ea580c;
        border: 1px solid #fed7aa;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }

      .header-lucide-icon {
        width: 20px;
        height: 20px;
      }

      .modal-title {
        margin: 0;
        font-size: 1.125rem;
        font-weight: 600;
        color: #0f172a;
      }

      .modal-subtitle {
        margin: 0.2rem 0 0;
        font-size: 0.8125rem;
        color: #64748b;
      }

      .btn-close {
        background: transparent;
        border: none;
        color: #64748b;
        cursor: pointer;
        padding: 0.35rem;
        border-radius: 6px;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: background 0.15s ease;

        &:hover {
          background: #f1f5f9;
          color: #0f172a;
        }
      }

      .close-icon {
        width: 18px;
        height: 18px;
      }

      .modal-body {
        padding: 1.25rem 1.5rem;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: 1.25rem;
      }

      .modal-footer {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 0.75rem;
        padding: 1rem 1.5rem;
        border-top: 1px solid #e2e8f0;
        background: #f8fafc;
      }

      /* ── Shared Form Elements ───────────────────── */
      .alert-banner {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.75rem 1rem;
        border-radius: 8px;
        font-size: 0.8125rem;
      }

      .error-banner {
        background: #fef2f2;
        color: #b91c1c;
        border: 1px solid #fecaca;
      }

      .banner-icon {
        width: 16px;
        height: 16px;
        flex-shrink: 0;
      }

      .boundary-callout {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.75rem 1rem;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
      }

      .boundary-icon-box {
        width: 32px;
        height: 32px;
        border-radius: 6px;
        background: #ffffff;
        border: 1px solid #cbd5e1;
        color: #475569;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }

      .boundary-icon {
        width: 16px;
        height: 16px;
      }

      .boundary-info {
        display: flex;
        flex-direction: column;
        flex: 1;
        min-width: 0;
      }

      .boundary-label {
        font-size: 0.6875rem;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: #64748b;
        font-weight: 600;
      }

      .boundary-value {
        font-size: 0.875rem;
        font-weight: 600;
        color: #0f172a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .boundary-ceiling-pill {
        font-size: 0.6875rem;
        font-weight: 600;
        padding: 0.2rem 0.5rem;
        border-radius: 4px;
        background: #fff7ed;
        color: #c2410c;
        border: 1px solid #fed7aa;
        flex-shrink: 0;
      }

      .form-group {
        display: flex;
        flex-direction: column;
        gap: 0.4rem;
      }

      .form-label {
        font-size: 0.8125rem;
        font-weight: 600;
        color: #334155;
      }

      .required {
        color: #ef4444;
      }

      .select-wrapper {
        position: relative;
      }

      .form-select,
      .form-input {
        width: 100%;
        padding: 0.6rem 0.875rem;
        border: 1px solid #cbd5e1;
        border-radius: 8px;
        font-size: 0.875rem;
        color: #0f172a;
        background: #ffffff;
        transition: border-color 0.15s ease, box-shadow 0.15s ease;

        &:focus {
          outline: none;
          border-color: #f97316;
          box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.15);
        }
      }

      .readonly-input-box {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.6rem 0.875rem;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        font-size: 0.875rem;
        color: #334155;
      }

      .input-icon {
        width: 16px;
        height: 16px;
        color: #64748b;
        flex-shrink: 0;
      }

      .font-medium {
        font-weight: 500;
      }

      .sub-text {
        font-size: 0.8125rem;
        color: #64748b;
      }

      /* ── Scoped Resource Picker ────────────────── */
      .resource-picker-container {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        border: 1px solid #cbd5e1;
        border-radius: 8px;
        overflow: hidden;
        background: #ffffff;
      }

      .resource-search-box {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 0.75rem;
        background: #f8fafc;
        border-bottom: 1px solid #e2e8f0;
      }

      .search-icon {
        width: 15px;
        height: 15px;
        color: #64748b;
        flex-shrink: 0;
      }

      .search-input {
        flex: 1;
        background: transparent;
        border: none;
        outline: none;
        font-size: 0.8125rem;
        color: #0f172a;

        &::placeholder {
          color: #94a3b8;
        }
      }

      .btn-clear-search {
        background: transparent;
        border: none;
        color: #64748b;
        cursor: pointer;
        padding: 0;
        display: flex;
        align-items: center;
      }

      .clear-icon {
        width: 13px;
        height: 13px;
      }

      .resource-loading,
      .resource-empty {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.5rem;
        padding: 2rem 1rem;
        font-size: 0.8125rem;
        color: #64748b;
      }

      .resource-list-scroll {
        max-height: 200px;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
      }

      .resource-option-row {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        padding: 0.5rem 0.75rem;
        border-bottom: 1px solid #f1f5f9;
        cursor: pointer;
        transition: background-color 0.12s ease;

        &:last-child {
          border-bottom: none;
        }

        &:hover {
          background-color: #f8fafc;
        }

        &.selected {
          background-color: #fff7ed;

          .option-name {
            color: #c2410c;
            font-weight: 600;
          }
        }
      }

      .option-radio {
        width: 16px;
        height: 16px;
        border-radius: 50%;
        border: 1.5px solid #cbd5e1;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }

      .radio-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background-color: transparent;
        transition: background-color 0.12s ease;

        &.active {
          background-color: #ea580c;
        }
      }

      .option-icon-box {
        width: 24px;
        height: 24px;
        border-radius: 4px;
        background: #f1f5f9;
        color: #475569;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;

        &.is-doc {
          color: #2563eb;
          background: #eff6ff;
        }
      }

      .option-icon {
        width: 14px;
        height: 14px;
      }

      .option-text {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-width: 0;
      }

      .option-name {
        font-size: 0.8125rem;
        font-weight: 500;
        color: #0f172a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .option-path {
        font-size: 0.6875rem;
        color: #64748b;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .option-type-badge {
        font-size: 0.625rem;
        font-weight: 600;
        text-transform: uppercase;
        color: #94a3b8;
        padding: 0.1rem 0.35rem;
        background: #f8fafc;
        border-radius: 3px;
        flex-shrink: 0;
      }

      /* ── Permission Level Cards ─────────────────── */
      .permission-options-grid {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }

      .level-card {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        padding: 0.75rem 1rem;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.15s ease;
        background: #ffffff;

        &:hover:not(.disabled) {
          border-color: #fed7aa;
          background: #fffdfa;
        }

        &.selected {
          border-color: #f97316;
          background: #fff7ed;
        }

        &.disabled {
          opacity: 0.5;
          cursor: not-allowed;
          background: #f8fafc;
          border-color: #f1f5f9;
        }
      }

      .level-radio {
        width: 16px;
        height: 16px;
        border-radius: 50%;
        border: 1.5px solid #cbd5e1;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-top: 0.15rem;
        flex-shrink: 0;
      }

      .radio-inner {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background-color: transparent;

        &.active {
          background-color: #ea580c;
        }
      }

      .level-meta {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
        flex: 1;
      }

      .level-title-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }

      .level-name {
        font-size: 0.875rem;
        font-weight: 600;
        color: #0f172a;

        &.is-edit {
          color: #c2410c;
        }

        &.is-download {
          color: #1d4ed8;
        }
      }

      .ceiling-exceeded-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        font-size: 0.6875rem;
        font-weight: 600;
        color: #991b1b;
        background: #fee2e2;
        padding: 0.1rem 0.4rem;
        border-radius: 4px;
      }

      .lock-icon {
        width: 10px;
        height: 10px;
      }

      .level-desc {
        font-size: 0.75rem;
        color: #64748b;
        line-height: 1.35;
      }

      /* ── Buttons ───────────────────────────────── */
      .btn {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 1rem;
        font-size: 0.8125rem;
        font-weight: 500;
        border-radius: 6px;
        cursor: pointer;
        transition: all 0.15s ease;
        border: 1px solid transparent;
      }

      .btn-secondary {
        background: #ffffff;
        border-color: #cbd5e1;
        color: #334155;

        &:hover:not(:disabled) {
          background: #f1f5f9;
          color: #0f172a;
        }
      }

      .btn-primary {
        background: #f97316;
        color: #ffffff;

        &:hover:not(:disabled) {
          background: #ea580c;
        }
      }

      .btn:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }

      .spinner,
      .spinner-sm {
        border: 2px solid rgba(255, 255, 255, 0.3);
        border-top-color: #ffffff;
        border-radius: 50%;
        animation: spin 0.8s linear infinite;
      }

      .spinner {
        width: 14px;
        height: 14px;
      }

      .spinner-sm {
        width: 12px;
        height: 12px;
        border-color: rgba(234, 88, 12, 0.2);
        border-top-color: #ea580c;
      }

      @keyframes spin {
        to { transform: rotate(360deg); }
      }

      .font-mono {
        font-family: monospace;
      }
    `,
  ],
})
export class GrantMemberAccessModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() inline = false;
  @Input() orgCode = '';
  @Input() teamId = 0;
  @Input() teamName = '';
  @Input() boundary: TeamAccessBoundaryDto | null = null;
  @Input() availableMembers: OrganizationMemberResponseDto[] = [];
  @Input() editingAccess: TeamMemberAccessDto | null = null;
  @Input() preSelectedEmail = '';

  @Output() close = new EventEmitter<void>();
  @Output() accessSaved = new EventEmitter<TeamMemberAccessDto>();

  private readonly teamDelegationService = inject(TeamDelegationService);

  readonly allPermissionLevels: TeamPermissionLevel[] = ['VIEW', 'DOWNLOAD', 'EDIT'];

  readonly selectedEmail = signal<string>('');
  readonly selectedResourceKey = signal<string>('');
  readonly selectedFolderId = signal<number | null>(null);
  readonly selectedDocumentId = signal<number | null>(null);
  readonly selectedLevel = signal<TeamPermissionLevel>('VIEW');
  readonly note = signal<string>('');
  readonly isLoading = signal<boolean>(false);
  readonly isLoadingResources = signal<boolean>(false);
  readonly resourceOptions = signal<FlatResourceOption[]>([]);
  readonly resourceSearchQuery = signal<string>('');
  readonly errorMessage = signal<string | null>(null);

  readonly isEditMode = computed(() => !!this.editingAccess);

  readonly allowedLevels = computed<TeamPermissionLevel[]>(() => {
    const ceiling = this.boundary?.permissionLevel || 'VIEW';
    if (ceiling === 'EDIT') {
      return ['VIEW', 'DOWNLOAD', 'EDIT'];
    }
    if (ceiling === 'DOWNLOAD') {
      return ['VIEW', 'DOWNLOAD'];
    }
    return ['VIEW'];
  });

  readonly filteredResourceOptions = computed(() => {
    const q = this.resourceSearchQuery().trim().toLowerCase();
    const all = this.resourceOptions();
    if (!q) return all;
    return all.filter((r) => r.name.toLowerCase().includes(q) || r.path.toLowerCase().includes(q));
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.resetForm();
    }
    if (changes['editingAccess'] && this.editingAccess) {
      this.resetForm();
    }
    if (changes['preSelectedEmail'] && this.preSelectedEmail && !this.selectedEmail()) {
      this.selectedEmail.set(this.preSelectedEmail);
    }
  }

  private resetForm() {
    this.errorMessage.set(null);
    this.resourceSearchQuery.set('');

    if (this.editingAccess) {
      this.selectedEmail.set(this.editingAccess.userEmail);
      this.selectedLevel.set(this.editingAccess.permissionLevel);
      this.note.set(this.editingAccess.note || '');
      if (this.editingAccess.resourceType === 'FOLDER') {
        this.selectedFolderId.set(this.editingAccess.resourceId);
        this.selectedDocumentId.set(null);
        this.selectedResourceKey.set(`FOLDER:${this.editingAccess.resourceId}`);
      } else {
        this.selectedDocumentId.set(this.editingAccess.resourceId);
        this.selectedFolderId.set(null);
        this.selectedResourceKey.set(`DOCUMENT:${this.editingAccess.resourceId}`);
      }
    } else {
      this.selectedEmail.set(this.preSelectedEmail || '');
      this.selectedResourceKey.set('');
      this.selectedFolderId.set(null);
      this.selectedDocumentId.set(null);
      this.selectedLevel.set('VIEW');
      this.note.set('');
      this.loadScopedResources();
    }
  }

  private loadScopedResources() {
    if (!this.orgCode || !this.teamId) return;
    this.isLoadingResources.set(true);
    this.teamDelegationService
      .getScopedResources(this.orgCode, this.teamId)
      .pipe(
        catchError((err) => {
          this.errorMessage.set(err?.error?.message || 'Failed to load boundary resources');
          return of(null);
        })
      )
      .subscribe((tree) => {
        this.isLoadingResources.set(false);
        if (tree) {
          const list: FlatResourceOption[] = [];
          this.flattenResourceTree(tree, '', list);
          this.resourceOptions.set(list);
          if (list.length > 0 && !this.selectedResourceKey()) {
            this.onResourceSelected(list[0].type + ':' + list[0].id);
          }
        }
      });
  }

  private flattenResourceTree(node: ScopedResourceNodeDto, parentPath: string, list: FlatResourceOption[]) {
    const currentPath = parentPath ? `${parentPath}/${node.name}` : `/${node.name}`;
    list.push({
      id: node.id,
      name: node.name,
      type: node.type,
      path: currentPath,
    });
    if (node.children) {
      for (const child of node.children) {
        this.flattenResourceTree(child, currentPath, list);
      }
    }
  }

  onResourceSelected(key: string) {
    this.selectedResourceKey.set(key);
    if (!key) {
      this.selectedFolderId.set(null);
      this.selectedDocumentId.set(null);
      return;
    }
    const [type, idStr] = key.split(':');
    const id = Number(idStr);
    if (type === 'FOLDER') {
      this.selectedFolderId.set(id);
      this.selectedDocumentId.set(null);
    } else {
      this.selectedDocumentId.set(id);
      this.selectedFolderId.set(null);
    }
  }

  getLevelDescription(level: TeamPermissionLevel): string {
    switch (level) {
      case 'VIEW':
        return 'Read metadata and preview documents. No downloading or modification.';
      case 'DOWNLOAD':
        return 'View and download document contents. No editing or folder creation.';
      case 'EDIT':
        return 'Upload, create subfolders, edit metadata, and modify content.';
    }
  }

  isValid(): boolean {
    if (!this.selectedEmail()) return false;
    if (!this.isEditMode() && !this.selectedFolderId() && !this.selectedDocumentId()) return false;
    return !!this.selectedLevel() && this.allowedLevels().includes(this.selectedLevel());
  }

  onClose() {
    this.close.emit();
  }

  onSave() {
    if (!this.isValid()) return;

    this.isLoading.set(true);
    this.errorMessage.set(null);

    if (this.isEditMode() && this.editingAccess) {
      this.teamDelegationService
        .updateMemberAccess(
          this.orgCode,
          this.teamId,
          this.editingAccess.permissionId,
          {
            permissionLevel: this.selectedLevel(),
            note: this.note() || undefined,
          }
        )
        .pipe(
          catchError((err) => {
            this.errorMessage.set(
              err?.error?.message || 'Failed to update member access. Ensure level does not exceed ceiling.'
            );
            this.isLoading.set(false);
            return of(null);
          })
        )
        .subscribe((res) => {
          this.isLoading.set(false);
          if (res) {
            this.accessSaved.emit(res);
            this.onClose();
          }
        });
    } else {
      this.teamDelegationService
        .grantMemberAccess(this.orgCode, this.teamId, {
          userEmail: this.selectedEmail(),
          folderId: this.selectedFolderId() || undefined,
          documentId: this.selectedDocumentId() || undefined,
          permissionLevel: this.selectedLevel(),
          note: this.note() || undefined,
        })
        .pipe(
          catchError((err) => {
            this.errorMessage.set(
              err?.error?.message || 'Failed to grant member access. Ensure target is inside boundary root and within ceiling.'
            );
            this.isLoading.set(false);
            return of(null);
          })
        )
        .subscribe((res) => {
          this.isLoading.set(false);
          if (res) {
            this.accessSaved.emit(res);
            this.onClose();
          }
        });
    }
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.isOpen) {
      this.onClose();
    }
  }
}
