import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  computed,
  ElementRef,
  ViewChild,
  effect,
  inject,
  HostListener
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { UserApiService } from '../../../core/services/user-api.service';
import { UserResponseDto } from '../../../core/models/user.model';
import { OrganizationApiService } from '../../../core/services/organization-api.service';
import { OrganizationResponseDto, GroupResponseDto } from '../../../core/models/organization.model';
import { AuthStore } from '../../../state/auth/auth.store';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { PermissionApiService } from '../../permissions/services/permission-api.service';
import { ShareApiService } from '../../permissions/services/share-api.service';
import { FolderApiService } from '../../../core/services/folder-api.service';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import {
  SharePermissionLevel,
  BulkShareWithExclusionsDto,
  ShareLinkResponseDto,
  RecipientType,
  SelectedRecipient,
  getRecipientSelectionKey
} from '../../permissions/interfaces/permission.interfaces';
import { FolderContentResponseDto } from '../../../core/models/folder.model';
import { isPersonalResource, getResourceOwnerOrgCode } from '../../../core/utils/resource-classification.util';
import { DropdownComponent } from '../../../shared/ui/dropdown/dropdown.component';
import { IconComponent } from '../../../shared/components/icon/icon.component';

export type ShareItemTarget = {
  type: 'folder' | 'document' | 'file';
  data: any;
};

@Component({
  selector: 'app-share-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, DropdownComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen()) {
      <div class="share-modal-backdrop" (click)="onBackdropClick($event)" (keydown.escape)="onClose()">
        <div
          class="share-modal-card"
          role="dialog"
          aria-modal="true"
          aria-labelledby="share-modal-title"
          (click)="$event.stopPropagation()"
        >
          <!-- Header -->
          <div class="share-modal-header">
            <div class="header-brand-group">
              <div class="share-icon-badge" [class.is-folder]="getItemType() === 'folder'">
                @if (getItemType() === 'folder') {
                  <app-icon name="folder" [size]="18" />
                } @else {
                  <app-icon name="file-text" [size]="18" />
                }
              </div>
              <div class="header-text min-w-0">
                <div class="header-title-row">
                  <h3 id="share-modal-title" class="modal-title" [title]="getItemName()">
                    Share "{{ getItemName() }}"
                  </h3>
                  <span class="item-type-badge" [class.badge-folder]="getItemType() === 'folder'">
                    {{ getItemType() === 'folder' ? 'Folder' : 'Document' }}
                  </span>
                </div>
                <p class="modal-subtitle">
                  Configure access settings, add collaborators, or generate secure links.
                </p>
              </div>
            </div>
            <button
              type="button"
              class="btn-close-modal"
              (click)="onClose()"
              aria-label="Close share dialog"
              title="Close modal (Esc)"
            >
              <app-icon name="x" [size]="18" />
            </button>
          </div>

          <!-- Segmented Tab Navigation -->
          <div class="share-tabs-container">
            <div class="share-tabs-track" role="tablist">
              <button
                type="button"
                role="tab"
                class="tab-btn"
                [class.active]="shareMode() === 'collaborators'"
                [attr.aria-selected]="shareMode() === 'collaborators'"
                (click)="shareMode.set('collaborators')"
              >
                <app-icon name="users" [size]="18" />
                <span>{{ isPersonalResource() ? 'People' : 'People & Groups' }}</span>
              </button>
              <button
                type="button"
                role="tab"
                class="tab-btn"
                [class.active]="shareMode() === 'link'"
                [attr.aria-selected]="shareMode() === 'link'"
                (click)="shareMode.set('link')"
              >
                <app-icon name="link" [size]="18" />
                <span>Secure Link</span>
              </button>
            </div>
          </div>

          <!-- Modal Body Content -->
          <div class="share-modal-body">
            @if (shareMode() === 'collaborators') {
              <!-- COLLABORATORS MODE -->
              <div class="section-block">
                <!-- Section 1: Add Collaborators Input & Dropdown -->
                <div class="form-group recipient-group">
                  <div class="section-label-row">
                    <label id="add-collaborators-label" class="section-tag">
                      ADD RECIPIENTS
                    </label>
                    @if (selectedRecipients().length > 0) {
                      <span class="count-badge">{{ selectedRecipients().length }} selected</span>
                    }
                  </div>

                  <div class="user-search-row" #userSearchRowRef>
                    <div class="search-input-wrapper">
                      <app-icon name="search" [size]="16" class="search-icon" />
                      <input
                        #userInputRef
                        type="text"
                        class="form-input user-search-input"
                        [placeholder]="isPersonalResource() ? 'Search people by name or email...' : 'Search people, teams, organizations, or collaborations...'"
                        [ngModel]="userSearchQuery()"
                        (ngModelChange)="onUserSearchInput($event)"
                        (focus)="onUserSearchFocus()"
                        (keydown.enter)="onUserSearchEnter($event)"
                        aria-labelledby="add-collaborators-label"
                        aria-autocomplete="list"
                        [attr.aria-expanded]="showUserResultsDropdown()"
                        autocomplete="off"
                      />
                      @if (isSearchingUsers()) {
                        <div class="input-spinner-wrapper">
                          <div class="inline-spinner"></div>
                        </div>
                      } @else if (userSearchQuery().length > 0) {
                        <button
                          type="button"
                          class="btn-clear-search"
                          (click)="onUserSearchInput('')"
                          aria-label="Clear input"
                        >
                          <app-icon name="x" [size]="14" />
                        </button>
                      }
                    </div>

                    <button
                      type="button"
                      class="btn-add-recipient"
                      (click)="addRecipientFromTypedInput()"
                      [disabled]="!userSearchQuery().trim()"
                      title="Add recipient email"
                    >
                      <app-icon name="plus" [size]="16" />
                      <span>Add</span>
                    </button>

                    <!-- User Search Autocomplete Dropdown -->
                    @if (showUserResultsDropdown() && userSearchQuery().trim().length > 0) {
                      <div class="user-search-dropdown" role="listbox" aria-label="Directory search results">
                        @if (isSearchingUsers()) {
                          <div class="dropdown-state-box">
                            <div class="inline-spinner"></div>
                            <span>Searching directory...</span>
                          </div>
                        } @else if (userSearchResults().length > 0) {
                          <div class="directory-results-list">

                            <!-- Group 1: People (Personal Users) -->
                            @if (peopleResults().length > 0) {
                              <div class="directory-group">
                                <div class="group-header-row">
                                  <app-icon name="user" [size]="16" class="group-icon" />
                                  <span class="group-title">People</span>
                                  <span class="group-count-badge">{{ peopleResults().length }}</span>
                                </div>
                                <div class="group-items">
                                  @for (recipient of peopleResults(); track recipient.selectionKey) {
                                    <button
                                      type="button"
                                      class="dropdown-item item-person"
                                      [class.selected]="isRecipientSelected(recipient.selectionKey)"
                                      (click)="addRecipientFromSearch(recipient)"
                                      role="option"
                                      [attr.aria-selected]="isRecipientSelected(recipient.selectionKey)"
                                    >
                                      <div class="user-avatar-circle">
                                        {{ getUserInitials(recipient.displayName) }}
                                      </div>
                                      <div class="user-meta">
                                        <div class="user-meta-top">
                                          <span class="user-name">{{ recipient.displayName }}</span>
                                          <span class="type-tag tag-person">Personal</span>
                                        </div>
                                        <span class="user-email">{{ recipient.targetUserEmail }}</span>
                                      </div>
                                      @if (isRecipientSelected(recipient.selectionKey)) {
                                        <span class="badge-added">
                                          <app-icon name="check" [size]="14" />
                                          Added
                                        </span>
                                      } @else {
                                        <span class="badge-select">+ Select</span>
                                      }
                                    </button>
                                  }
                                </div>
                              </div>
                            }

                            <!-- Group 2: Organization Members -->
                            @if (orgMemberResults().length > 0) {
                              <div class="directory-group">
                                <div class="group-header-row">
                                  <app-icon name="briefcase" [size]="16" class="group-icon" />
                                  <span class="group-title">Organization Members</span>
                                  <span class="group-count-badge">{{ orgMemberResults().length }}</span>
                                </div>
                                <div class="group-items">
                                  @for (recipient of orgMemberResults(); track recipient.selectionKey) {
                                    <button
                                      type="button"
                                      class="dropdown-item item-org-member"
                                      [class.selected]="isRecipientSelected(recipient.selectionKey)"
                                      (click)="addRecipientFromSearch(recipient)"
                                      role="option"
                                      [attr.aria-selected]="isRecipientSelected(recipient.selectionKey)"
                                    >
                                      <div class="user-avatar-circle org-user-avatar">
                                        {{ getUserInitials(recipient.displayName) }}
                                      </div>
                                      <div class="user-meta">
                                        <div class="user-meta-top">
                                          <span class="user-name">{{ recipient.displayName }}</span>
                                          <span class="org-context-pill">
                                            <app-icon name="briefcase" [size]="12" class="pill-bag-icon" />
                                            {{ recipient.displayContext }}
                                          </span>
                                          @if (recipient.orgRole) {
                                            <span class="role-badge" [class.role-admin]="recipient.orgRole === 'ADMIN' || recipient.orgRole === 'OWNER'">
                                              {{ recipient.orgRole }}
                                            </span>
                                          }
                                        </div>
                                        <span class="user-email">{{ recipient.targetUserEmail }}</span>
                                      </div>
                                      @if (isRecipientSelected(recipient.selectionKey)) {
                                        <span class="badge-added">
                                          <app-icon name="check" [size]="14" />
                                          Added
                                        </span>
                                      } @else {
                                        <span class="badge-select">+ Select</span>
                                      }
                                    </button>
                                  }
                                </div>
                              </div>
                            }

                            <!-- Group 3: Organizations -->
                            @if (organizationResults().length > 0) {
                              <div class="directory-group">
                                <div class="group-header-row">
                                  <app-icon name="building-2" [size]="16" class="group-icon" />
                                  <span class="group-title">Organizations</span>
                                  <span class="group-count-badge">{{ organizationResults().length }}</span>
                                </div>
                                <div class="group-items">
                                  @for (recipient of organizationResults(); track recipient.selectionKey) {
                                    <button
                                      type="button"
                                      class="dropdown-item item-org"
                                      [class.selected]="isRecipientSelected(recipient.selectionKey)"
                                      (click)="addRecipientFromSearch(recipient)"
                                      role="option"
                                      [attr.aria-selected]="isRecipientSelected(recipient.selectionKey)"
                                    >
                                      <div class="user-avatar-circle org-avatar">
                                        {{ getUserInitials(recipient.displayName) }}
                                      </div>
                                      <div class="user-meta">
                                        <div class="user-meta-top">
                                          <span class="user-name">{{ recipient.displayName }}</span>
                                          <span class="type-tag tag-org">Organization</span>
                                        </div>
                                        <span class="user-email">Entire organization (Code: {{ recipient.targetOrgCode }})</span>
                                      </div>
                                      @if (isRecipientSelected(recipient.selectionKey)) {
                                        <span class="badge-added">
                                          <app-icon name="check" [size]="14" />
                                          Added
                                        </span>
                                      } @else {
                                        <span class="badge-select">+ Select</span>
                                      }
                                    </button>
                                  }
                                </div>
                              </div>
                            }

                            <!-- Group 4: Teams -->
                            @if (teamResults().length > 0) {
                              <div class="directory-group">
                                <div class="group-header-row">
                                  <app-icon name="users" [size]="16" class="group-icon" />
                                  <span class="group-title">Teams</span>
                                  <span class="group-count-badge">{{ teamResults().length }}</span>
                                </div>
                                <div class="group-items">
                                  @for (recipient of teamResults(); track recipient.selectionKey) {
                                    <button
                                      type="button"
                                      class="dropdown-item item-team"
                                      [class.selected]="isRecipientSelected(recipient.selectionKey)"
                                      (click)="addRecipientFromSearch(recipient)"
                                      role="option"
                                      [attr.aria-selected]="isRecipientSelected(recipient.selectionKey)"
                                    >
                                      <div class="entity-avatar-square team-avatar">
                                        <app-icon name="users" [size]="16" />
                                      </div>
                                      <div class="user-meta">
                                        <div class="user-meta-top">
                                          <span class="user-name">{{ recipient.displayName }}</span>
                                          <span class="type-tag tag-team">Team</span>
                                        </div>
                                        <span class="user-email">{{ recipient.displayContext }}</span>
                                      </div>
                                      @if (isRecipientSelected(recipient.selectionKey)) {
                                        <span class="badge-added">
                                          <app-icon name="check" [size]="14" />
                                          Added
                                        </span>
                                      } @else {
                                        <span class="badge-select">+ Select</span>
                                      }
                                    </button>
                                  }
                                </div>
                              </div>
                            }

                          </div>
                        } @else {
                          <div class="dropdown-state-box">
                            <app-icon name="alert-circle" [size]="16" />
                            <span>No directory results found for "{{ userSearchQuery() }}"</span>
                            <button type="button" class="link-add-direct" (click)="addRecipientFromTypedInput()">
                              Add "{{ userSearchQuery().trim() }}" directly
                            </button>
                          </div>
                        }
                      </div>
                    }
                  </div>
                </div>

                <!-- Section 2: Added Recipients Container -->
                <div class="form-group">
                  <div class="section-tag">ADDED RECIPIENTS</div>

                  @if (selectedRecipients().length === 0) {
                    <!-- Empty State Box -->
                    <div class="recipients-empty-card">
                      <div class="empty-icon-circle">
                        <app-icon name="user-plus" [size]="24" />
                      </div>
                      <p class="empty-text">No recipient emails added yet.</p>
                      <span class="empty-subtext">Search directory or enter an address above.</span>
                    </div>
                  } @else {
                    <!-- Selected Recipients List -->
                    <div class="recipients-list-box" aria-label="Added recipients">
                      @for (recipient of selectedRecipients(); track recipient.selectionKey) {
                        <div class="recipient-card" [ngClass]="'card-type-' + recipient.recipientType.toLowerCase()">
                          <div class="recipient-left">
                            @if (recipient.recipientType === 'ORGANIZATION') {
                              <div class="user-avatar-circle org-avatar">
                                {{ getUserInitials(recipient.displayName) }}
                              </div>
                            } @else if (recipient.recipientType === 'GROUP') {
                              <div class="entity-avatar-square team-avatar">
                                <app-icon name="users" [size]="16" />
                              </div>
                            } @else {
                              <div class="recipient-avatar" [class.org-user-avatar]="recipient.recipientType === 'USER_ORGANIZATION_MEMBER'">
                                {{ getUserInitials(recipient.displayName) }}
                              </div>
                            }
                            <div class="recipient-details">
                              <div class="recipient-name-row">
                                <span class="recipient-name">{{ recipient.displayName }}</span>
                                <span class="recipient-context-tag">
                                  @if (recipient.recipientType === 'USER_ORGANIZATION_MEMBER') {
                                    <app-icon name="briefcase" [size]="12" class="pill-bag-icon" />
                                  }
                                  {{ recipient.displayContext }}
                                </span>
                                @if (recipient.orgRole) {
                                  <span class="role-badge" [class.role-admin]="recipient.orgRole === 'ADMIN' || recipient.orgRole === 'OWNER'">
                                    {{ recipient.orgRole }}
                                  </span>
                                }
                              </div>
                              <span class="recipient-email">
                                {{ recipient.targetUserEmail || (recipient.recipientType === 'ORGANIZATION' ? 'Organization-wide' : 'Team') }}
                              </span>
                            </div>
                          </div>
                          <button
                            type="button"
                            class="btn-remove-recipient"
                            (click)="removeRecipient(recipient.selectionKey)"
                            [attr.aria-label]="'Remove ' + recipient.displayName"
                            title="Remove recipient"
                          >
                            <app-icon name="x" [size]="14" />
                          </button>
                        </div>
                      }
                    </div>
                  }
                </div>

                <!-- Section 3: Scope of Permission & Temporal Expire Clock -->
                <div class="settings-grid-card">
                  <!-- Scope of Permission -->
                  <div class="form-group">
                    <label class="section-tag">
                      SCOPE OF PERMISSION
                    </label>
                    <sd-dropdown class="sd-custom-select-dropdown" placement="bottom-start">
                      <button dropdown-trigger type="button" class="form-select-trigger" aria-label="Scope of Permission">
                        <span class="select-label-text">{{ getPermissionLabel(sharePermissionLevel()) }}</span>
                        <app-icon name="chevron-down" [size]="16" class="select-arrow" />
                      </button>

                      <div dropdown-menu class="custom-select-menu">
                        <button
                          type="button"
                          class="custom-select-option"
                          [class.selected]="sharePermissionLevel() === 'VIEW'"
                          (click)="sharePermissionLevel.set('VIEW')"
                        >
                          <div class="option-content">
                            <span class="option-title">Can Read / View</span>
                            <span class="option-sub">Read-only view access</span>
                          </div>
                          @if (sharePermissionLevel() === 'VIEW') {
                            <app-icon name="check" [size]="16" class="check-icon" />
                          }
                        </button>
                        <button
                          type="button"
                          class="custom-select-option"
                          [class.selected]="sharePermissionLevel() === 'DOWNLOAD'"
                          (click)="sharePermissionLevel.set('DOWNLOAD')"
                        >
                          <div class="option-content">
                            <span class="option-title">Can Download</span>
                            <span class="option-sub">View and export files</span>
                          </div>
                          @if (sharePermissionLevel() === 'DOWNLOAD') {
                            <app-icon name="check" [size]="16" class="check-icon" />
                          }
                        </button>
                        <button
                          type="button"
                          class="custom-select-option"
                          [class.selected]="sharePermissionLevel() === 'EDIT'"
                          (click)="sharePermissionLevel.set('EDIT')"
                        >
                          <div class="option-content">
                            <span class="option-title">Can Edit / Manage</span>
                            <span class="option-sub">Full modification rights</span>
                          </div>
                          @if (sharePermissionLevel() === 'EDIT') {
                            <app-icon name="check" [size]="16" class="check-icon" />
                          }
                        </button>
                      </div>
                    </sd-dropdown>
                  </div>

                  <!-- Temporal Expire Clock -->
                  <div class="form-group">
                    <label class="section-tag">
                      TEMPORAL EXPIRE CLOCK
                    </label>
                    <div class="dual-clock-row">
                      <div class="clock-unit-wrapper" [class.full-width]="expireTimeUnit() === 'NEVER'">
                        <sd-dropdown class="sd-custom-select-dropdown" placement="bottom-start">
                          <button dropdown-trigger type="button" class="form-select-trigger" aria-label="Temporal Expire Clock">
                            <span class="select-label-text">{{ getExpireUnitLabel(expireTimeUnit()) }}</span>
                            <app-icon name="chevron-down" [size]="16" class="select-arrow" />
                          </button>

                          <div dropdown-menu class="custom-select-menu">
                            <button
                              type="button"
                              class="custom-select-option"
                              [class.selected]="expireTimeUnit() === 'HOURS'"
                              (click)="onExpireTimeUnitChange('HOURS')"
                            >
                              <span>Hours</span>
                            </button>
                            <button
                              type="button"
                              class="custom-select-option"
                              [class.selected]="expireTimeUnit() === 'DAYS'"
                              (click)="onExpireTimeUnitChange('DAYS')"
                            >
                              <span>Days</span>
                            </button>
                            <button
                              type="button"
                              class="custom-select-option"
                              [class.selected]="expireTimeUnit() === 'NEVER'"
                              (click)="onExpireTimeUnitChange('NEVER')"
                            >
                              <span>Never</span>
                            </button>
                            <button
                              type="button"
                              class="custom-select-option"
                              [class.selected]="expireTimeUnit() === 'DATE'"
                              (click)="onExpireTimeUnitChange('DATE')"
                            >
                              <span>Specific Date</span>
                            </button>
                          </div>
                        </sd-dropdown>
                      </div>

                      @if (expireTimeUnit() === 'HOURS' || expireTimeUnit() === 'DAYS') {
                        <div class="clock-value-wrapper">
                          <div class="number-stepper">
                            <button
                              type="button"
                              class="stepper-btn stepper-minus"
                              (click)="decrementExpireValue()"
                              [disabled]="(expireValue() ?? 1) <= 1"
                              aria-label="Decrease duration"
                            >
                              <app-icon name="minus" [size]="14" />
                            </button>
                            <input
                              type="number"
                              class="stepper-input"
                              min="1"
                              max="9999"
                              [ngModel]="expireValue()"
                              (ngModelChange)="expireValue.set($event)"
                              placeholder="24"
                            />
                            <button
                              type="button"
                              class="stepper-btn stepper-plus"
                              (click)="incrementExpireValue()"
                              aria-label="Increase duration"
                            >
                              <app-icon name="plus" [size]="14" />
                            </button>
                          </div>
                        </div>
                      } @else if (expireTimeUnit() === 'DATE') {
                        <div class="clock-date-wrapper">
                          <input
                            type="date"
                            class="form-input clock-date-input"
                            [ngModel]="expireDateInput()"
                            (ngModelChange)="expireDateInput.set($event)"
                          />
                        </div>
                      }
                    </div>
                  </div>
                </div>

                <!-- Section 4: Folder Content Exclusions (For Folders Only) -->
                @if (getItemType() === 'folder') {
                  <div class="exclusions-card">
                    <button
                      type="button"
                      class="exclusions-toggle-bar"
                      (click)="showExclusionsPanel.set(!showExclusionsPanel())"
                    >
                      <div class="ex-left">
                        <app-icon name="shield-check" [size]="18" class="ex-shield-icon" />
                        <div class="ex-titles">
                          <span class="ex-main-title">Folder Content Exclusions (Optional)</span>
                          <span class="ex-sub-title">Exclude specific child files or subfolders from this share</span>
                        </div>
                      </div>
                      <div class="ex-right">
                        <span class="ex-count-badge" [class.has-excluded]="getExcludedTotalCount() > 0">
                          {{ getExcludedTotalCount() }} Excluded
                        </span>
                        <app-icon name="chevron-down" [size]="16" class="chevron-icon" [class.open]="showExclusionsPanel()" />
                      </div>
                    </button>

                    @if (showExclusionsPanel()) {
                      <div class="exclusions-content-body">
                        <div class="ex-toolbar">
                          <div class="ex-search-box">
                            <app-icon name="search" [size]="16" />
                            <input
                              type="text"
                              class="ex-search-input"
                              placeholder="Search child files & folders..."
                              [ngModel]="exclusionSearchQuery()"
                              (ngModelChange)="exclusionSearchQuery.set($event)"
                            />
                          </div>
                          <div class="ex-quick-actions">
                            <button type="button" class="btn-ex-action" (click)="selectAllExclusions()">Exclude All</button>
                            <span class="sep">•</span>
                            <button type="button" class="btn-ex-action" (click)="clearExclusions()">Include All</button>
                          </div>
                        </div>

                        @if (isLoadingFolderContents()) {
                          <div class="ex-loading">
                            <div class="inline-spinner"></div>
                            <span>Loading folder items...</span>
                          </div>
                        } @else if (folderContents()?.subFolders?.length || folderContents()?.documents?.length) {
                          <div class="ex-items-list">
                            @for (sf of getFilteredSubfolders(); track sf.id) {
                              <div
                                class="ex-item-row"
                                [class.excluded]="isSubfolderExcluded(sf.id)"
                                (click)="toggleSubfolderExclusion(sf.id)"
                              >
                                <label class="custom-chk" (click)="$event.preventDefault()">
                                  <input type="checkbox" [checked]="isSubfolderExcluded(sf.id)" />
                                  <span class="chk-box">
                                    <app-icon name="check" [size]="12" class="chk-icon" />
                                  </span>
                                </label>
                                <div class="ex-item-icon folder-icon">
                                  <app-icon name="folder" [size]="16" />
                                </div>
                                <span class="ex-item-name">{{ sf.name }}</span>
                                <span class="ex-status-pill" [class.excluded]="isSubfolderExcluded(sf.id)">
                                  {{ isSubfolderExcluded(sf.id) ? 'Excluded' : 'Included' }}
                                </span>
                              </div>
                            }
                            @for (doc of getFilteredDocuments(); track doc.id) {
                              <div
                                class="ex-item-row"
                                [class.excluded]="isDocumentExcluded(+doc.id)"
                                (click)="toggleDocumentExclusion(+doc.id)"
                              >
                                <label class="custom-chk" (click)="$event.preventDefault()">
                                  <input type="checkbox" [checked]="isDocumentExcluded(+doc.id)" />
                                  <span class="chk-box">
                                    <app-icon name="check" [size]="12" class="chk-icon" />
                                  </span>
                                </label>
                                <div class="ex-item-icon doc-icon">
                                  <app-icon name="file-text" [size]="16" />
                                </div>
                                <span class="ex-item-name">{{ doc.name || doc.fileName || 'Untitled Document' }}</span>
                                <span class="ex-status-pill" [class.excluded]="isDocumentExcluded(+doc.id)">
                                  {{ isDocumentExcluded(+doc.id) ? 'Excluded' : 'Included' }}
                                </span>
                              </div>
                            }
                            @if (getFilteredSubfolders().length === 0 && getFilteredDocuments().length === 0) {
                              <div class="ex-empty-state">
                                <div class="ex-empty-icon-box">
                                  <app-icon name="folder-search" [size]="32" />
                                </div>
                                <h4 class="ex-empty-title">No folders or files found in this workspace space.</h4>
                                <p class="ex-empty-subtitle">Use the upload chips above to insert sample folders and documents!</p>
                              </div>
                            }
                          </div>
                        } @else {
                          <div class="ex-empty-state">
                            <div class="ex-empty-icon-box">
                              <app-icon name="folder-search" [size]="32" />
                            </div>
                            <h4 class="ex-empty-title">No folders or files found in this workspace space.</h4>
                            <p class="ex-empty-subtitle">Use the upload chips above to insert sample folders and documents!</p>
                          </div>
                        }
                      </div>
                    }
                  </div>
                }

                <!-- Status Feedback Alerts -->
                @if (grantSuccessMsg()) {
                  <div class="alert-box success">
                    <app-icon name="check" [size]="16" />
                    <span>{{ grantSuccessMsg() }}</span>
                  </div>
                }
                @if (grantErrorMsg()) {
                  <div class="alert-box error">
                    <app-icon name="alert-circle" [size]="16" />
                    <span>{{ grantErrorMsg() }}</span>
                  </div>
                }
              </div>
            } @else {
              <!-- SECURE LINK MODE -->
              <div class="section-block">
                <!-- Section 1: Link Permission Level -->
                <div class="form-group">
                  <label class="section-tag">
                    LINK ACCESS PERMISSION
                  </label>
                  <sd-dropdown class="sd-custom-select-dropdown" placement="bottom-start">
                    <button dropdown-trigger type="button" class="form-select-trigger" aria-label="Link Access Permission">
                      <span class="select-label-text">{{ getPermissionLabel(sharePermissionLevel()) }}</span>
                      <app-icon name="chevron-down" [size]="16" class="select-arrow" />
                    </button>

                    <div dropdown-menu class="custom-select-menu">
                      <button
                        type="button"
                        class="custom-select-option"
                        [class.selected]="sharePermissionLevel() === 'VIEW'"
                        (click)="sharePermissionLevel.set('VIEW')"
                      >
                        <div class="option-content">
                          <span class="option-title">Can Read / View</span>
                          <span class="option-sub">Read-only view access</span>
                        </div>
                        @if (sharePermissionLevel() === 'VIEW') {
                          <app-icon name="check" [size]="16" class="check-icon" />
                        }
                      </button>
                      <button
                        type="button"
                        class="custom-select-option"
                        [class.selected]="sharePermissionLevel() === 'DOWNLOAD'"
                        (click)="sharePermissionLevel.set('DOWNLOAD')"
                      >
                        <div class="option-content">
                          <span class="option-title">Can Download</span>
                          <span class="option-sub">View and export files</span>
                        </div>
                        @if (sharePermissionLevel() === 'DOWNLOAD') {
                          <app-icon name="check" [size]="16" class="check-icon" />
                        }
                      </button>
                      <button
                        type="button"
                        class="custom-select-option"
                        [class.selected]="sharePermissionLevel() === 'EDIT'"
                        (click)="sharePermissionLevel.set('EDIT')"
                      >
                        <div class="option-content">
                          <span class="option-title">Can Edit / Manage</span>
                          <span class="option-sub">Full modification rights</span>
                        </div>
                        @if (sharePermissionLevel() === 'EDIT') {
                          <app-icon name="check" [size]="16" class="check-icon" />
                        }
                      </button>
                    </div>
                  </sd-dropdown>
                </div>

                <!-- Section 2: Temporal Expiration -->
                <div class="form-group">
                  <label class="section-tag">
                    EXPIRATION PARAMETERS
                  </label>
                  <div class="dual-clock-row">
                    <div class="flex-2">
                      <sd-dropdown class="sd-custom-select-dropdown" placement="bottom-start">
                        <button dropdown-trigger type="button" class="form-select-trigger" aria-label="Expiration Parameters">
                          <span class="select-label-text">{{ getExpireUnitLabel(expireTimeUnit()) }}</span>
                          <app-icon name="chevron-down" [size]="16" class="select-arrow" />
                        </button>

                        <div dropdown-menu class="custom-select-menu">
                          <button
                            type="button"
                            class="custom-select-option"
                            [class.selected]="expireTimeUnit() === 'HOURS'"
                            (click)="onExpireTimeUnitChange('HOURS')"
                          >
                            <span>Hours</span>
                          </button>
                          <button
                            type="button"
                            class="custom-select-option"
                            [class.selected]="expireTimeUnit() === 'DAYS'"
                            (click)="onExpireTimeUnitChange('DAYS')"
                          >
                            <span>Days</span>
                          </button>
                          <button
                            type="button"
                            class="custom-select-option"
                            [class.selected]="expireTimeUnit() === 'NEVER'"
                            (click)="onExpireTimeUnitChange('NEVER')"
                          >
                            <span>Never</span>
                          </button>
                        </div>
                      </sd-dropdown>
                    </div>

                    @if (expireTimeUnit() === 'HOURS' || expireTimeUnit() === 'DAYS') {
                      <div class="clock-value-wrapper flex-1">
                        <div class="number-stepper">
                          <button
                            type="button"
                            class="stepper-btn stepper-minus"
                            (click)="decrementExpireValue()"
                            [disabled]="(expireValue() ?? 1) <= 1"
                            aria-label="Decrease duration"
                          >
                            <app-icon name="minus" [size]="14" />
                          </button>
                          <input
                            type="number"
                            class="stepper-input"
                            min="1"
                            max="9999"
                            [ngModel]="expireValue()"
                            (ngModelChange)="expireValue.set($event)"
                            placeholder="24"
                          />
                          <button
                            type="button"
                            class="stepper-btn stepper-plus"
                            (click)="incrementExpireValue()"
                            aria-label="Increase duration"
                          >
                            <app-icon name="plus" [size]="14" />
                          </button>
                        </div>
                      </div>
                    }
                  </div>
                </div>

                <!-- Section 3: Password Protection (Optional) -->
                <div class="form-group">
                  <label class="section-tag" for="link-password-input">
                    SECURITY PROTECTION (OPTIONAL)
                  </label>
                  <div class="input-icon-wrapper">
                    <app-icon name="lock-keyhole" [size]="16" class="input-left-icon" />
                    <input
                      id="link-password-input"
                      type="password"
                      class="form-input icon-input"
                      placeholder="Set access password..."
                      [ngModel]="shareLinkPassword()"
                      (ngModelChange)="shareLinkPassword.set($event)"
                      autocomplete="off"
                    />
                  </div>
                </div>
              </div>
            }
          </div>

          <!-- Footer Actions -->
          <div class="share-modal-footer">
            <button
              type="button"
              class="btn-modal-cancel"
              (click)="onClose()"
              [disabled]="isSubmittingGrant() || isCreatingLink()"
            >
              Cancel
            </button>

            @if (shareMode() === 'collaborators') {
              <button
                type="button"
                class="btn-modal-primary"
                [disabled]="isSubmittingGrant() || (selectedRecipients().length === 0 && !userSearchQuery().trim())"
                (click)="grantCollaboratorPermission()"
              >
                @if (isSubmittingGrant()) {
                  <div class="btn-spinner"></div>
                  <span>Committing...</span>
                } @else {
                  <app-icon name="shield-check" [size]="16" />
                  <span>Grant Access</span>
                }
              </button>
            } @else {
              <button
                type="button"
                class="btn-modal-primary"
                [disabled]="isCreatingLink()"
                (click)="createSecureShareLink()"
              >
                @if (isCreatingLink()) {
                  <div class="btn-spinner"></div>
                  <span>Generating Link...</span>
                } @else {
                  <app-icon name="link" [size]="16" />
                  <span>Create Secure Link</span>
                }
              </button>
            }
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .share-modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.45);
      backdrop-filter: blur(8px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: var(--sd-z-modal-backdrop, 3000);
      padding: 1.25rem;
      animation: backdropFadeIn 0.2s ease-out;
    }
    @keyframes backdropFadeIn {
      from { opacity: 0; }
      to   { opacity: 1; }
    }

    .share-modal-card {
      background: #ffffff;
      border-radius: 1.25rem;
      border: 1px solid rgba(226, 232, 240, 0.9);
      box-shadow: 0 25px 50px -12px rgba(15, 23, 42, 0.22), 0 0 0 1px rgba(15, 23, 42, 0.03);
      width: 100%;
      max-width: 520px;
      display: flex;
      flex-direction: column;
      max-height: 90vh;
      /* Note: overflow: hidden clipping container prepared for Angular CDK Overlay portal strategy */
      overflow: hidden;
      animation: modalScaleUp 0.22s cubic-bezier(0.16, 1, 0.3, 1);
    }
    @keyframes modalScaleUp {
      from { transform: scale(0.96) translateY(8px); opacity: 0; }
      to   { transform: scale(1) translateY(0); opacity: 1; }
    }

    /* ── Header ── */
    .share-modal-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      padding: 1.375rem 1.5rem 1rem;
      background: linear-gradient(180deg, #ffffff 0%, #fafafa 100%);
      border-bottom: 1px solid #f1f5f9;
    }
    .header-brand-group {
      display: flex;
      align-items: center;
      gap: 0.875rem;
      min-width: 0;
    }
    .share-icon-badge {
      width: 2.25rem;
      height: 2.25rem;
      border-radius: 0.5rem;
      background: #f8fafc;
      color: #64748b;
      border: 1px solid #e2e8f0;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      transition: all 0.2s ease;
    }
    .share-icon-badge.is-folder {
      background: #f8fafc;
      color: #64748b;
      border-color: #e2e8f0;
    }
    .share-icon-badge svg {
      width: 1.125rem;
      height: 1.125rem;
    }
    .header-title-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      min-width: 0;
    }
    .modal-title {
      font-size: 1.125rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
      letter-spacing: -0.01em;
      line-height: 1.3;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .item-type-badge {
      font-size: 0.6875rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      padding: 0.125rem 0.4375rem;
      border-radius: 0.375rem;
      background: #f1f5f9;
      color: #475569;
      border: 1px solid #e2e8f0;
      flex-shrink: 0;
    }
    .item-type-badge.badge-folder {
      background: #f1f5f9;
      color: #475569;
      border-color: #e2e8f0;
    }
    .modal-subtitle {
      font-size: 0.8125rem;
      color: #64748b;
      margin: 0.125rem 0 0;
      line-height: 1.4;
    }
    .btn-close-modal {
      background: transparent;
      border: none;
      color: #94a3b8;
      cursor: pointer;
      padding: 0.375rem;
      border-radius: 0.5rem;
      transition: all 0.15s ease;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .btn-close-modal:hover {
      background: #f1f5f9;
      color: #0f172a;
    }
    .btn-close-modal svg {
      width: 1.25rem;
      height: 1.25rem;
    }

    /* ── Tabs Navigation ── */
    .share-tabs-container {
      padding: 1rem 1.5rem 0;
      background: #ffffff;
    }
    .share-tabs-track {
      background: #f1f5f9;
      border: 1px solid #e2e8f0;
      border-radius: 0.75rem;
      padding: 3px;
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 3px;
    }
    .tab-btn {
      height: 2.375rem;
      border-radius: 0.625rem;
      font-size: 0.8125rem;
      font-weight: 600;
      color: #64748b;
      background: transparent;
      border: none;
      transition: all 0.18s ease;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      cursor: pointer;
    }
    .tab-btn svg {
      width: 1.125rem;
      height: 1.125rem;
    }
    .tab-btn:hover:not(.active) {
      color: #1e293b;
      background: rgba(255, 255, 255, 0.4);
    }
    .tab-btn.active {
      background: #ffffff;
      color: #0f172a;
      box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
    }

    /* ── Modal Body & Form ── */
    .share-modal-body {
      padding: 1.25rem 1.5rem;
      overflow-y: auto;
      flex: 1;
    }
    .section-block {
      display: flex;
      flex-direction: column;
      gap: 1.125rem;
    }
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
      position: relative;
    }
    .section-label-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .section-tag {
      font-size: 0.6875rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: #64748b;
      margin: 0;
    }
    .count-badge {
      font-size: 0.6875rem;
      font-weight: 600;
      color: #0f172a;
      background: #f1f5f9;
      padding: 0.125rem 0.5rem;
      border-radius: 0.375rem;
      border: 1px solid #e2e8f0;
    }

    /* ── User Search Input Row ── */
    .user-search-row {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      position: relative;
    }
    .search-input-wrapper {
      position: relative;
      flex: 1;
      display: flex;
      align-items: center;
    }
    .search-icon,
    app-icon.search-icon {
      position: absolute;
      left: 0.875rem;
      top: 50%;
      transform: translateY(-50%);
      width: 1.125rem;
      height: 1.125rem;
      color: #94a3b8;
      pointer-events: none;
      z-index: 3;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: color 0.18s ease;
    }
    .search-icon svg,
    app-icon.search-icon svg {
      width: 1.125rem !important;
      height: 1.125rem !important;
      display: block;
      flex-shrink: 0;
    }
    .search-input-wrapper:focus-within .search-icon,
    .search-input-wrapper:focus-within app-icon.search-icon {
      color: #0f172a;
    }
    .form-input.user-search-input,
    .user-search-input {
      width: 100%;
      height: 2.625rem;
      padding-top: 0 !important;
      padding-bottom: 0 !important;
      padding-left: 2.875rem !important;
      padding-right: 2.5rem !important;
      font-size: 0.875rem;
      color: #0f172a;
      background: #ffffff;
      border: 1px solid #cbd5e1 !important;
      border-radius: 0.625rem !important;
      outline: none !important;
      box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.04) !important;
      transition: border-color 0.18s ease, box-shadow 0.18s ease, background-color 0.18s ease !important;
    }
    .form-input.user-search-input::placeholder,
    .user-search-input::placeholder {
      color: #94a3b8;
    }
    .form-input.user-search-input:hover,
    .user-search-input:hover {
      border-color: #94a3b8 !important;
    }
    .form-input.user-search-input:focus,
    .user-search-input:focus {
      border-color: #0f172a !important;
      box-shadow: 0 0 0 1px #0f172a, 0 0 0 4px rgba(15, 23, 42, 0.08), 0 1px 2px 0 rgba(0, 0, 0, 0.05) !important;
      background: #ffffff !important;
    }
    .input-spinner-wrapper {
      position: absolute;
      right: 0.75rem;
      top: 50%;
      transform: translateY(-50%);
      z-index: 3;
      display: flex;
      align-items: center;
      justify-content: center;
      pointer-events: none;
    }
    .btn-clear-search {
      position: absolute;
      right: 0.625rem;
      top: 50%;
      transform: translateY(-50%);
      z-index: 3;
      background: transparent;
      border: none;
      color: #94a3b8;
      cursor: pointer;
      padding: 0.25rem;
      border-radius: 0.375rem;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: color 0.15s ease;
    }
    .btn-clear-search:hover {
      color: #0f172a;
    }
    .btn-clear-search svg {
      width: 1rem;
      height: 1rem;
    }

    .btn-add-recipient {
      height: 2.625rem;
      padding: 0 1rem;
      background: #f1f5f9;
      color: #334155;
      font-size: 0.8125rem;
      font-weight: 600;
      border: 1px solid #cbd5e1;
      border-radius: 0.625rem;
      transition: all 0.15s ease;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      flex-shrink: 0;
    }
    .btn-add-recipient svg {
      width: 1.125rem;
      height: 1.125rem;
    }
    .btn-add-recipient:hover:not(:disabled) {
      background: #0f172a;
      color: #ffffff;
      border-color: #0f172a;
    }
    .btn-add-recipient:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    /* ── Autocomplete Dropdown ── */
    .user-search-dropdown {
      position: absolute;
      top: calc(100% + 4px);
      left: 0;
      right: 0;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 0.75rem;
      box-shadow: 0 12px 28px rgba(15, 23, 42, 0.15);
      max-height: 320px;
      overflow-y: auto;
      z-index: var(--sd-z-dropdown, 1000);
      padding: 0.5rem;
      animation: dropdownFadeIn 0.15s ease-out;
    }
    @keyframes dropdownFadeIn {
      from { opacity: 0; transform: translateY(-4px); }
      to   { opacity: 1; transform: translateY(0); }
    }
    .dropdown-state-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 1.25rem 1rem;
      color: #64748b;
      font-size: 0.8125rem;
      text-align: center;
    }
    .dropdown-state-box svg {
      width: 1.5rem;
      height: 1.5rem;
      color: #94a3b8;
    }
    .link-add-direct {
      background: none;
      border: none;
      color: #ff5500;
      font-size: 0.8125rem;
      font-weight: 600;
      cursor: pointer;
      text-decoration: underline;
      margin-top: 0.25rem;
    }

    /* ── Directory Grouping Styles ── */
    .directory-results-list {
      display: flex;
      flex-direction: column;
      gap: 0.625rem;
    }
    .directory-group {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .directory-group:not(:last-child) {
      padding-bottom: 0.5rem;
      border-bottom: 1px solid #f1f5f9;
    }
    .group-header-row {
      display: flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.25rem 0.5rem;
      user-select: none;
    }
    .group-icon {
      width: 0.875rem;
      height: 0.875rem;
      color: #64748b;
    }
    .group-title {
      font-size: 0.6875rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #64748b;
    }
    .group-count-badge {
      font-size: 0.625rem;
      font-weight: 600;
      color: #94a3b8;
      background: #f1f5f9;
      padding: 0.0625rem 0.375rem;
      border-radius: 0.25rem;
    }
    .group-items {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .dropdown-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: 100%;
      padding: 0.5rem 0.625rem;
      border: none;
      background: transparent;
      border-radius: 0.5rem;
      cursor: pointer;
      text-align: left;
      transition: background 0.12s ease;
    }
    .dropdown-item:hover {
      background: #f8fafc;
    }
    .dropdown-item.selected {
      background: #fff7ed;
    }
    .user-avatar-circle {
      width: 2.125rem;
      height: 2.125rem;
      border-radius: 50%;
      background: #e2e8f0;
      color: #334155;
      font-size: 0.75rem;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .entity-avatar-square {
      width: 2.125rem;
      height: 2.125rem;
      border-radius: 0.5rem;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .entity-avatar-square svg {
      width: 1.125rem;
      height: 1.125rem;
    }
    .entity-avatar-square.org-avatar {
      background: #eff6ff;
      color: #2563eb;
      border: 1px solid #dbeafe;
    }
    .user-avatar-circle.org-avatar {
      background: #eff6ff;
      color: #2563eb;
      border: 1px solid #dbeafe;
    }
    .entity-avatar-square.team-avatar {
      background: #f0fdf4;
      color: #16a34a;
      border: 1px solid #dcfce7;
    }
    .user-avatar-circle.org-user-avatar {
      background: #f5f3ff;
      color: #7c3aed;
      border: 1px solid #ede9fe;
    }

    .user-meta {
      display: flex;
      flex-direction: column;
      flex: 1;
      margin: 0 0.75rem;
      overflow: hidden;
    }
    .user-meta-top {
      display: flex;
      align-items: center;
      gap: 0.375rem;
      flex-wrap: wrap;
    }
    .user-name {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #0f172a;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .type-tag {
      font-size: 0.625rem;
      font-weight: 600;
      padding: 0.0625rem 0.375rem;
      border-radius: 0.25rem;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }
    .tag-person {
      background: #f1f5f9;
      color: #475569;
    }
    .tag-org {
      background: #eff6ff;
      color: #1d4ed8;
    }
    .tag-team {
      background: #f0fdf4;
      color: #15803d;
    }
    .org-context-pill {
      font-size: 0.6875rem;
      font-weight: 500;
      color: #475569;
      background: #f1f5f9;
      padding: 0.0625rem 0.375rem;
      border-radius: 0.25rem;
      white-space: nowrap;
    }
    .role-badge {
      font-size: 0.625rem;
      font-weight: 700;
      padding: 0.0625rem 0.375rem;
      border-radius: 0.25rem;
      text-transform: uppercase;
      background: #f1f5f9;
      color: #64748b;
    }
    .role-badge.role-admin {
      background: #fef2f2;
      color: #b91c1c;
      border: 1px solid #fee2e2;
    }
    .user-email {
      font-size: 0.75rem;
      color: #64748b;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .badge-select {
      font-size: 0.75rem;
      font-weight: 600;
      color: #ff5500;
      flex-shrink: 0;
    }
    .badge-added {
      font-size: 0.75rem;
      font-weight: 600;
      color: #16a34a;
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      flex-shrink: 0;
    }

    /* ── Added Recipients Container ── */
    .recipients-empty-card {
      border: 1.5px dashed #cbd5e1;
      background: #f8fafc;
      border-radius: 0.75rem;
      padding: 1.25rem 1rem;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 0.25rem;
    }
    .empty-icon-circle {
      width: 2.25rem;
      height: 2.25rem;
      border-radius: 50%;
      background: #f1f5f9;
      color: #94a3b8;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 0.25rem;
    }
    .empty-icon-circle svg {
      width: 1.25rem;
      height: 1.25rem;
    }
    .empty-text {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #475569;
      margin: 0;
    }
    .empty-subtext {
      font-size: 0.75rem;
      color: #94a3b8;
    }

    .recipients-list-box {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      max-height: 160px;
      overflow-y: auto;
      padding-right: 2px;
    }
    .recipient-card {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.5rem 0.75rem;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 0.625rem;
      transition: all 0.15s ease;
    }
    .recipient-card:hover {
      border-color: #cbd5e1;
      background: #ffffff;
    }
    .recipient-left {
      display: flex;
      align-items: center;
      gap: 0.625rem;
      overflow: hidden;
    }
    .recipient-avatar {
      width: 2rem;
      height: 2rem;
      border-radius: 50%;
      background: #fff7ed;
      color: #ff5500;
      font-size: 0.75rem;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      border: 1px solid #ffedd5;
    }
    .recipient-details {
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    .recipient-name-row {
      display: flex;
      align-items: center;
      gap: 0.375rem;
    }
    .recipient-context-tag {
      font-size: 0.6875rem;
      color: #64748b;
      background: #e2e8f0;
      padding: 0.0625rem 0.375rem;
      border-radius: 0.25rem;
      font-weight: 500;
      white-space: nowrap;
    }
    .recipient-name {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #0f172a;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .recipient-email {
      font-size: 0.75rem;
      color: #64748b;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .btn-remove-recipient {
      background: transparent;
      border: none;
      color: #94a3b8;
      cursor: pointer;
      padding: 0.25rem;
      border-radius: 0.375rem;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.15s ease;
    }
    .btn-remove-recipient:hover {
      background: #fecaca;
      color: #dc2626;
    }
    .btn-remove-recipient svg {
      width: 1rem;
      height: 1rem;
    }

    /* ── Settings Grid (Permission & Clock) ── */
    .settings-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.875rem;
    }

    /* ── Custom Dropdown Select Controls ── */
    .sd-custom-select-dropdown {
      width: 100%;
      display: block;
    }
    ::ng-deep .sd-custom-select-dropdown,
    ::ng-deep .sd-custom-select-dropdown .sd-dropdown-container {
      width: 100%;
      display: block;
    }
    .form-select-trigger {
      width: 100%;
      height: 2.5rem;
      padding: 0 0.75rem;
      font-size: 0.8125rem;
      color: #0f172a;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 0.625rem;
      outline: none;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: space-between;
      transition: all 0.15s ease;
      font-family: inherit;
    }
    .form-select-trigger:hover {
      border-color: #94a3b8;
      background: #f8fafc;
    }
    .form-select-trigger:focus,
    .form-select-trigger[aria-expanded="true"] {
      border-color: #0f172a;
      box-shadow: 0 0 0 3px rgba(15, 23, 42, 0.08);
    }
    .form-select-trigger[aria-expanded="true"] .select-arrow {
      transform: rotate(180deg);
    }
    .select-label-text {
      font-weight: 500;
      color: #0f172a;
      font-size: 0.8125rem;
    }
    ::ng-deep .sd-custom-select-dropdown .sd-dropdown-menu {
      width: 100%;
      min-width: 200px;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 0.75rem;
      box-shadow: 0 12px 28px rgba(15, 23, 42, 0.15);
      padding: 0.375rem;
    }
    .custom-select-menu {
      display: flex;
      flex-direction: column;
      gap: 2px;
      width: 100%;
    }
    .custom-select-option {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: 100%;
      padding: 0.5rem 0.75rem;
      border: none;
      background: transparent;
      border-radius: 0.5rem;
      cursor: pointer;
      text-align: left;
      transition: background 0.12s ease;
      font-family: inherit;
    }
    .custom-select-option:hover {
      background: #f8fafc;
    }
    .custom-select-option.selected {
      background: #f1f5f9;
    }
    .custom-select-option.selected .option-title,
    .custom-select-option.selected span {
      color: #0f172a;
      font-weight: 600;
    }
    .option-content {
      display: flex;
      flex-direction: column;
    }
    .option-title {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #0f172a;
    }
    .option-sub {
      font-size: 0.71875rem;
      color: #64748b;
    }
    .check-icon {
      width: 1rem;
      height: 1rem;
      color: #0f172a;
      flex-shrink: 0;
    }

    .select-wrapper {
      position: relative;
      width: 100%;
    }
    .form-select {
      width: 100%;
      height: 2.5rem;
      padding: 0 2rem 0 0.75rem;
      font-size: 0.8125rem;
      color: #0f172a;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 0.625rem;
      outline: none;
      appearance: none;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .form-select:focus {
      border-color: #0f172a;
      box-shadow: 0 0 0 3px rgba(15, 23, 42, 0.08);
    }
    .org-context-pill {
      font-size: 0.6875rem;
      font-weight: 500;
      color: #475569;
      background: #f1f5f9;
      padding: 0.125rem 0.4375rem;
      border-radius: 0.375rem;
      white-space: nowrap;
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
    }
    .recipient-context-tag {
      font-size: 0.6875rem;
      color: #64748b;
      background: #e2e8f0;
      padding: 0.125rem 0.4375rem;
      border-radius: 0.375rem;
      font-weight: 500;
      white-space: nowrap;
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
    }
    .pill-bag-icon {
      color: #64748b;
      flex-shrink: 0;
    }
    .select-arrow {
      display: inline-flex !important;
      align-items: center !important;
      justify-content: center !important;
      line-height: 0 !important;
      width: 1rem;
      height: 1rem;
      color: #64748b;
      pointer-events: none;
      flex-shrink: 0;
      transition: transform 0.15s ease;
    }
    .form-select-trigger app-icon,
    .chevron-icon {
      display: inline-flex !important;
      align-items: center !important;
      justify-content: center !important;
      line-height: 0 !important;
    }
    ::ng-deep .form-select-trigger app-icon svg,
    ::ng-deep .select-arrow svg,
    ::ng-deep .chevron-icon svg {
      display: block !important;
      margin: auto !important;
    }

    .dual-clock-row {
      display: flex;
      gap: 0.375rem;
      align-items: center;
    }
    .flex-2 { flex: 1.4; }
    .flex-1 { flex: 1; }

    .number-stepper {
      display: flex;
      align-items: center;
      width: 100%;
      height: 2.5rem;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 0.625rem;
      overflow: hidden;
      transition: all 0.15s ease;
    }
    .number-stepper:focus-within {
      border-color: #0f172a;
      box-shadow: 0 0 0 3px rgba(15, 23, 42, 0.08);
    }
    .stepper-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 2.25rem;
      height: 100%;
      border: none;
      background: #f8fafc;
      color: #475569;
      cursor: pointer;
      transition: background 0.15s ease, color 0.15s ease;
      flex-shrink: 0;
    }
    .stepper-minus {
      border-right: 1px solid #e2e8f0;
    }
    .stepper-plus {
      border-left: 1px solid #e2e8f0;
    }
    .stepper-btn:hover:not(:disabled) {
      background: #f1f5f9;
      color: #0f172a;
    }
    .stepper-btn:active:not(:disabled) {
      background: #e2e8f0;
    }
    .stepper-btn:disabled {
      opacity: 0.35;
      cursor: not-allowed;
    }
    .stepper-input {
      flex: 1;
      width: 100%;
      height: 100%;
      border: none;
      background: transparent;
      text-align: center;
      font-size: 0.8125rem;
      font-weight: 600;
      color: #0f172a;
      outline: none;
      padding: 0;
      -moz-appearance: textfield;
    }
    .stepper-input::-webkit-outer-spin-button,
    .stepper-input::-webkit-inner-spin-button {
      -webkit-appearance: none;
      margin: 0;
    }

    .clock-value-input, .clock-date-input {
      width: 100%;
      height: 2.5rem;
      padding: 0 0.5rem;
      font-size: 0.8125rem;
      color: #0f172a;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 0.625rem;
      outline: none;
      text-align: center;
      transition: all 0.15s ease;
    }
    .clock-value-input:focus, .clock-date-input:focus {
      border-color: #0f172a;
      box-shadow: 0 0 0 3px rgba(15, 23, 42, 0.08);
    }

    /* ── Input Icon Wrapper ── */
    .input-icon-wrapper {
      position: relative;
      display: flex;
      align-items: center;
    }
    .input-left-icon {
      position: absolute;
      left: 0.75rem;
      width: 1rem;
      height: 1rem;
      color: #94a3b8;
      pointer-events: none;
    }
    .form-input {
      width: 100%;
      height: 2.5rem;
      padding: 0 0.75rem;
      font-size: 0.8125rem;
      color: #0f172a;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 0.625rem;
      outline: none;
      transition: all 0.15s ease;
    }
    .form-input.icon-input {
      padding-left: 2.25rem;
    }
    .form-input:not(.user-search-input):focus {
      border-color: #0f172a;
      box-shadow: 0 0 0 3px rgba(15, 23, 42, 0.08);
    }

    /* ── Folder Exclusions Card (Google Studio AI & Griffin Clean Light Style) ── */
    .exclusions-card {
      border: 1px solid #eaecf0;
      border-radius: 0.75rem;
      background: #ffffff;
      box-shadow: 0 1px 3px 0 rgba(16, 24, 40, 0.04), 0 1px 2px -1px rgba(16, 24, 40, 0.02);
      overflow: hidden;
      transition: border-color 0.2s ease, box-shadow 0.2s ease;
    }
    .exclusions-card:hover {
      border-color: #d0d5dd;
    }
    .exclusions-toggle-bar {
      width: 100%;
      padding: 0.75rem 0.875rem;
      background: #f8fafc;
      border: none;
      display: flex;
      align-items: center;
      justify-content: space-between;
      cursor: pointer;
      text-align: left;
      transition: background 0.15s ease;
    }
    .exclusions-toggle-bar:hover {
      background: #f1f5f9;
    }
    .ex-left {
      display: flex;
      align-items: center;
      gap: 0.625rem;
    }
    .ex-shield-icon {
      width: 1.125rem;
      height: 1.125rem;
      color: #475569;
      flex-shrink: 0;
    }
    .ex-titles {
      display: flex;
      flex-direction: column;
      gap: 0.125rem;
    }
    .ex-main-title {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #0f172a;
      letter-spacing: -0.01em;
    }
    .ex-sub-title {
      font-size: 0.75rem;
      color: #64748b;
      font-weight: 400;
    }
    .ex-right {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .ex-count-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      height: 1.375rem;
      padding: 0 0.625rem;
      font-size: 0.6875rem;
      font-weight: 600;
      line-height: 1;
      color: #475569;
      background: #f1f5f9;
      border: 1px solid #e2e8f0;
      border-radius: 9999px;
      letter-spacing: 0.01em;
      white-space: nowrap;
      transition: all 0.2s ease;
    }
    .ex-count-badge.has-excluded {
      color: #9a3412;
      background: #fff7ed;
      border-color: #ffedd5;
    }
    .chevron-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 1rem;
      height: 1rem;
      color: #64748b;
      transition: transform 0.2s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .chevron-icon.open {
      transform: rotate(180deg);
    }

    .exclusions-content-body {
      padding: 0.875rem;
      border-top: 1px solid #eaecf0;
      background: #ffffff;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .ex-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.625rem;
    }
    .ex-search-box {
      position: relative;
      flex: 1;
      display: flex;
      align-items: center;
    }
    .ex-search-box app-icon {
      position: absolute;
      left: 0.625rem;
      top: 50%;
      transform: translateY(-50%);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: #94a3b8;
      pointer-events: none;
      z-index: 2;
    }
    .ex-search-input {
      width: 100%;
      height: 2.125rem;
      padding-left: 2.125rem;
      padding-right: 0.625rem;
      font-size: 0.75rem;
      color: #0f172a;
      background: #ffffff;
      border: 1px solid #d0d5dd;
      border-radius: 0.375rem;
      outline: none;
      transition: border-color 0.15s ease, box-shadow 0.15s ease;
    }
    .ex-search-input:focus {
      border-color: #0f172a;
      box-shadow: 0 0 0 3px rgba(15, 23, 42, 0.06);
    }
    .ex-quick-actions {
      display: flex;
      align-items: center;
      gap: 0.375rem;
      font-size: 0.75rem;
    }
    .btn-ex-action {
      background: none;
      border: none;
      color: #0f172a;
      font-weight: 600;
      cursor: pointer;
      padding: 0.125rem 0.25rem;
      border-radius: 0.25rem;
      transition: background 0.15s ease, color 0.15s ease;
    }
    .btn-ex-action:hover {
      background: #f1f5f9;
      color: #020617;
    }
    .sep { color: #cbd5e1; font-size: 0.6875rem; }

    .ex-items-list {
      display: flex;
      flex-direction: column;
      gap: 4px;
      max-height: 160px;
      overflow-y: auto;
      padding-right: 2px;
    }
    .ex-item-row {
      display: flex;
      align-items: center;
      gap: 0.625rem;
      padding: 0.5rem 0.625rem;
      border-radius: 0.375rem;
      background: #ffffff;
      border: 1px solid #f1f5f9;
      cursor: pointer;
      font-size: 0.75rem;
      transition: all 0.15s ease;
    }
    .ex-item-row:hover {
      background: #f8fafc;
      border-color: #e2e8f0;
    }
    .ex-item-row.excluded {
      background: #fffbebf5;
      border-color: #fef3c7;
    }
    .ex-item-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: #64748b;
      flex-shrink: 0;
    }
    .ex-item-name {
      flex: 1;
      color: #1e293b;
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .ex-status-pill {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      height: 1.25rem;
      padding: 0 0.5rem;
      font-size: 0.6875rem;
      font-weight: 600;
      line-height: 1;
      color: #15803d;
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      border-radius: 0.375rem;
      letter-spacing: 0.01em;
    }
    .ex-status-pill.excluded {
      color: #9a3412;
      background: #fff7ed;
      border-color: #ffedd5;
    }
    .ex-loading {
      padding: 0.875rem;
      text-align: center;
      font-size: 0.75rem;
      color: #64748b;
    }
    .ex-empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 1.25rem 0.75rem;
      text-align: center;
      background: transparent;
    }
    .ex-empty-icon-box {
      color: #94a3b8;
      display: flex;
      justify-content: center;
      margin-bottom: 0.5rem;
    }
    .ex-empty-icon-box app-icon {
      opacity: 0.8;
    }
    .ex-empty-title {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #64748b;
      margin: 0 0 0.25rem 0;
    }
    .ex-empty-subtitle {
      font-size: 0.71875rem;
      color: #94a3b8;
      margin: 0;
    }

    /* Custom Checkbox - Modern Clean Light Style */
    .custom-chk {
      position: relative;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 1.125rem;
      height: 1.125rem;
      cursor: pointer;
      flex-shrink: 0;
    }
    .custom-chk input {
      opacity: 0;
      width: 0;
      height: 0;
      position: absolute;
      pointer-events: none;
    }
    .chk-box {
      position: absolute;
      top: 0; left: 0; right: 0; bottom: 0;
      background-color: #ffffff;
      border: 1.5px solid #cbd5e1;
      border-radius: 0.25rem;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.15s ease;
    }
    .custom-chk:hover input:not(:checked) ~ .chk-box {
      border-color: #94a3b8;
      background-color: #f8fafc;
    }
    .custom-chk input:checked ~ .chk-box {
      background-color: #0f172a;
      border-color: #0f172a;
    }
    .chk-icon {
      display: none;
      color: #ffffff;
      width: 12px;
      height: 12px;
    }
    .custom-chk input:checked ~ .chk-box .chk-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    /* ── Created Link Card ── */
    .created-link-card {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 0.75rem;
      padding: 0.875rem 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.625rem;
      animation: fadeIn 0.15s ease-out;
    }
    .card-header-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .badge-success-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      font-size: 0.75rem;
      font-weight: 600;
      color: #15803d;
      background: #dcfce7;
      padding: 0.1875rem 0.625rem;
      border-radius: 0.5rem;
    }
    .badge-success-pill svg {
      width: 0.875rem;
      height: 0.875rem;
    }
    .expire-tag {
      font-size: 0.6875rem;
      color: #64748b;
    }
    .link-url-box {
      display: flex;
      gap: 0.5rem;
      align-items: center;
    }
    .url-input {
      flex: 1;
      height: 2.375rem;
      padding: 0 0.625rem;
      font-size: 0.8125rem;
      font-family: monospace;
      color: #0f172a;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 0.5rem;
      outline: none;
    }
    .btn-copy-link {
      height: 2.375rem;
      padding: 0 0.875rem;
      background: #ff5500;
      color: #ffffff;
      font-size: 0.8125rem;
      font-weight: 600;
      border: none;
      border-radius: 0.5rem;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      transition: all 0.15s ease;
    }
    .btn-copy-link svg {
      width: 0.875rem;
      height: 0.875rem;
    }
    .btn-copy-link:hover {
      background: #e04b00;
    }
    .btn-copy-link.copied {
      background: #16a34a;
    }

    /* ── Alerts ── */
    .alert-box {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.625rem 0.875rem;
      border-radius: 0.5rem;
      font-size: 0.8125rem;
      line-height: 1.4;
    }
    .alert-box svg {
      width: 1.125rem;
      height: 1.125rem;
      flex-shrink: 0;
    }
    .alert-box.success {
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      color: #15803d;
    }
    .alert-box.error {
      background: #fef2f2;
      border: 1px solid #fecaca;
      color: #b91c1c;
    }

    /* ── Footer Actions ── */
    .share-modal-footer {
      padding: 1.125rem 1.5rem;
      border-top: 1px solid #f1f5f9;
      background: #fafafa;
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.75rem;
    }
    .btn-modal-cancel {
      height: 2.5rem;
      padding: 0 1.125rem;
      background: #ffffff;
      color: #475569;
      font-size: 0.875rem;
      font-weight: 600;
      border: 1px solid #cbd5e1;
      border-radius: 0.625rem;
      cursor: pointer;
      transition: all 0.15s ease;
    }
    .btn-modal-cancel:hover:not(:disabled) {
      background: #f8fafc;
      color: #0f172a;
      border-color: #94a3b8;
    }
    .btn-modal-primary {
      height: 2.5rem;
      padding: 0 1.25rem;
      background: #ff5500;
      color: #ffffff;
      font-size: 0.875rem;
      font-weight: 600;
      border: none;
      border-radius: 0.625rem;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      transition: all 0.15s ease;
      box-shadow: 0 2px 4px rgba(255, 85, 0, 0.2);
    }
    .btn-modal-primary svg {
      width: 1.125rem;
      height: 1.125rem;
    }
    .btn-modal-primary:hover:not(:disabled) {
      background: #e04b00;
      box-shadow: 0 4px 10px rgba(255, 85, 0, 0.3);
    }
    .btn-modal-primary:disabled, .btn-modal-cancel:disabled {
      opacity: 0.5;
      cursor: not-allowed;
      box-shadow: none;
    }

    .inline-spinner {
      width: 1rem;
      height: 1rem;
      border: 2px solid rgba(148, 163, 184, 0.3);
      border-top-color: #ff5500;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }
    .btn-spinner {
      width: 1rem;
      height: 1rem;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: #ffffff;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `]
})
export class ShareModalComponent {
  private readonly userApiService = inject(UserApiService);
  private readonly permissionApiService = inject(PermissionApiService);
  private readonly shareApiService = inject(ShareApiService);
  private readonly folderApiService = inject(FolderApiService);
  private readonly authStore = inject(AuthStore);
  private readonly organizationApiService = inject(OrganizationApiService);
  private readonly workspaceContext = inject(WorkspaceContextStore);

  isOpen = input<boolean>(false);
  item = input<ShareItemTarget | null>(null);

  closed = output<void>();
  permissionGranted = output<void>();
  shareLinkCreated = output<ShareLinkResponseDto>();

  shareMode = signal<'collaborators' | 'link'>('collaborators');
  userSearchQuery = signal<string>('');
  userSearchResults = signal<SelectedRecipient[]>([]);
  isSearchingUsers = signal<boolean>(false);
  selectedRecipients = signal<SelectedRecipient[]>([]);
  showUserResultsDropdown = signal<boolean>(false);

  readonly isPersonalResource = computed<boolean>(() => {
    return isPersonalResource(this.item()?.data);
  });

  readonly resourceOwnerOrgCode = computed<string | null>(() => {
    if (this.isPersonalResource()) {
      return null;
    }
    return getResourceOwnerOrgCode(this.item()?.data);
  });

  peopleResults = computed(() =>
    this.userSearchResults().filter((r) => r.recipientType === 'USER_PERSONAL')
  );
  orgMemberResults = computed(() => {
    if (this.isPersonalResource()) return [];
    return this.userSearchResults().filter((r) => r.recipientType === 'USER_ORGANIZATION_MEMBER');
  });
  organizationResults = computed(() => {
    if (this.isPersonalResource()) return [];
    return this.userSearchResults().filter((r) => r.recipientType === 'ORGANIZATION');
  });
  teamResults = computed(() => {
    if (this.isPersonalResource()) return [];
    return this.userSearchResults().filter((r) => r.recipientType === 'GROUP');
  });

  sharePermissionLevel = signal<SharePermissionLevel>('VIEW');
  expireTimeUnit = signal<'HOURS' | 'DAYS' | 'NEVER' | 'DATE'>('HOURS');
  expireValue = signal<number | null>(24);
  expireDateInput = signal<string>('');

  shareLinkPassword = signal<string>('');
  isSubmittingGrant = signal<boolean>(false);
  grantSuccessMsg = signal<string | null>(null);
  grantErrorMsg = signal<string | null>(null);

  isCreatingLink = signal<boolean>(false);
  createdShareLink = signal<ShareLinkResponseDto | null>(null);
  copiedShareToken = signal<string | null>(null);

  // Exclusions for Folder
  folderContents = signal<FolderContentResponseDto | null>(null);
  isLoadingFolderContents = signal<boolean>(false);
  excludedSubfolderIds = signal<number[]>([]);
  excludedDocumentIds = signal<number[]>([]);
  exclusionSearchQuery = signal<string>('');
  showExclusionsPanel = signal<boolean>(false);

  @ViewChild('userInputRef') userInputRef?: ElementRef<HTMLInputElement>;
  @ViewChild('userSearchRowRef') userSearchRowRef?: ElementRef<HTMLDivElement>;

  constructor() {
    effect(() => {
      if (this.isOpen()) {
        this.resetState();
        const cur = this.item();
        if (cur && cur.type === 'folder' && cur.data?.id) {
          this.loadFolderContents(+cur.data.id);
        }
        setTimeout(() => this.userInputRef?.nativeElement.focus(), 60);
      } else {
        this.resetState();
      }
    });
  }

  resetState(): void {
    this.shareMode.set('collaborators');
    this.userSearchQuery.set('');
    this.userSearchResults.set([]);
    this.isSearchingUsers.set(false);
    this.selectedRecipients.set([]);
    this.showUserResultsDropdown.set(false);
    this.sharePermissionLevel.set('VIEW');
    this.expireTimeUnit.set('HOURS');
    this.expireValue.set(24);
    this.expireDateInput.set('');
    this.shareLinkPassword.set('');
    this.isSubmittingGrant.set(false);
    this.grantSuccessMsg.set(null);
    this.grantErrorMsg.set(null);
    this.isCreatingLink.set(false);
    this.createdShareLink.set(null);
    this.copiedShareToken.set(null);
    this.folderContents.set(null);
    this.excludedSubfolderIds.set([]);
    this.excludedDocumentIds.set([]);
    this.exclusionSearchQuery.set('');
    this.showExclusionsPanel.set(false);
  }

  getItemName(): string {
    const d = this.item()?.data;
    return d?.fileName || d?.name || 'Untitled Item';
  }

  getItemType(): string {
    return this.item()?.type || 'document';
  }

  getUserInitials(name: string): string {
    if (!name) return 'U';
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  }

  onUserSearchInput(query: string): void {
    const trimmed = query.trim();
    this.userSearchQuery.set(query);
    if (!trimmed) {
      this.userSearchResults.set([]);
      this.isSearchingUsers.set(false);
      this.showUserResultsDropdown.set(false);
      return;
    }

    this.isSearchingUsers.set(true);
    this.showUserResultsDropdown.set(true);

    const currentUserEmail = this.authStore.currentUser()?.email?.toLowerCase();

    // Personal resources only allow direct personal shares (USER_PERSONAL)
    if (this.isPersonalResource()) {
      this.userApiService
        .searchUsers(trimmed)
        .pipe(catchError(() => of([])))
        .subscribe({
          next: (users) => {
            const candidates: SelectedRecipient[] = [];
            const selectedKeys = new Set(this.selectedRecipients().map((r) => r.selectionKey));

            (users || []).forEach((u: UserResponseDto) => {
              const emailLower = u.email ? u.email.toLowerCase() : '';
              const displayName = `${u.firstName || ''} ${u.lastName || ''}`.trim() || u.email;
              const isCurrentUser = !!(currentUserEmail && emailLower === currentUserEmail);

              if (!isCurrentUser) {
                const personalKey = getRecipientSelectionKey('USER_PERSONAL', u.email);
                if (!selectedKeys.has(personalKey)) {
                  candidates.push({
                    selectionKey: personalKey,
                    recipientType: 'USER_PERSONAL',
                    targetUserEmail: u.email,
                    displayName,
                    displayContext: 'Personal User',
                    userProfile: u
                  });
                }
              }
            });

            this.userSearchResults.set(candidates);
            this.isSearchingUsers.set(false);
          },
          error: (err: any) => {
            console.error('Failed to search recipients:', err);
            this.userSearchResults.set([]);
            this.isSearchingUsers.set(false);
          }
        });
      return;
    }

    const resourceOrg = this.resourceOwnerOrgCode();

    const groupRequest$: Observable<any[]> = resourceOrg
      ? this.organizationApiService.getOrganizationGroups(resourceOrg).pipe(
          catchError(() => of([])),
          map((groups: GroupResponseDto[]) =>
            (groups || []).map((g: any) => ({
              ...g,
              orgCode: g.orgCode || resourceOrg,
              orgName: g.orgName || resourceOrg
            }))
          )
        )
      : of([]);

    forkJoin({
      users: this.userApiService.searchUsers(trimmed).pipe(catchError(() => of([]))),
      orgs: this.organizationApiService.searchOrganizations(trimmed).pipe(catchError(() => of([]))),
      groupResults: groupRequest$
    }).subscribe({
      next: ({ users, orgs, groupResults }) => {
        const candidates: SelectedRecipient[] = [];
        const selectedKeys = new Set(this.selectedRecipients().map((r) => r.selectionKey));

        // 1. Process Users (USER_PERSONAL & USER_ORGANIZATION_MEMBER)
        (users || []).forEach((u: UserResponseDto) => {
          const emailLower = u.email ? u.email.toLowerCase() : '';
          const displayName = `${u.firstName || ''} ${u.lastName || ''}`.trim() || u.email;
          const isCurrentUser = !!(currentUserEmail && emailLower === currentUserEmail);

          if (isCurrentUser) {
            return;
          }

          // Check if target user has an ACTIVE membership in the resource's ownerOrgCode
          const targetActiveOrgMembership =
            resourceOrg && u.activeOrganizations && u.activeOrganizations.length > 0
              ? u.activeOrganizations.find(
                  (org) =>
                    org.orgCode?.toUpperCase() === resourceOrg.toUpperCase() &&
                    (!org.status || org.status === 'ACTIVE')
                )
              : undefined;

          if (targetActiveOrgMembership) {
            // Internal member candidate for resource owner organization ONLY
            const orgKey = getRecipientSelectionKey('USER_ORGANIZATION_MEMBER', u.email, resourceOrg!);
            if (!selectedKeys.has(orgKey)) {
              candidates.push({
                selectionKey: orgKey,
                recipientType: 'USER_ORGANIZATION_MEMBER',
                targetUserEmail: u.email,
                targetOrgCode: resourceOrg!,
                orgRole: targetActiveOrgMembership.role || undefined,
                displayName,
                displayContext: `${targetActiveOrgMembership.orgName || resourceOrg} Member`,
                userProfile: u
              });
            }
          }

          // Personal User candidate (representing individual / external user share)
          const personalKey = getRecipientSelectionKey('USER_PERSONAL', u.email);
          if (!selectedKeys.has(personalKey)) {
            candidates.push({
              selectionKey: personalKey,
              recipientType: 'USER_PERSONAL',
              targetUserEmail: u.email,
              displayName,
              displayContext: 'Personal User',
              userProfile: u
            });
          }
        });

        // 2. Process Organizations (ORGANIZATION)
        (orgs || []).forEach((org: OrganizationResponseDto) => {
          if (org.orgCode) {
            const orgKey = getRecipientSelectionKey('ORGANIZATION', undefined, org.orgCode);
            if (!selectedKeys.has(orgKey)) {
              candidates.push({
                selectionKey: orgKey,
                recipientType: 'ORGANIZATION',
                targetOrgCode: org.orgCode,
                displayName: org.name || org.orgCode,
                displayContext: 'Organization'
              });
            }
          }
        });

        // 3. Process Groups (GROUP)
        const qLower = trimmed.toLowerCase();
        (groupResults || []).forEach((g: any) => {
          const groupNameLower = (g.name || '').toLowerCase();
          const orgNameLower = (g.orgName || '').toLowerCase();
          const orgCodeLower = (g.orgCode || '').toLowerCase();

          const matchesQuery =
            groupNameLower.includes(qLower) ||
            orgNameLower.includes(qLower) ||
            orgCodeLower.includes(qLower);

          if (matchesQuery && g.id) {
            const groupKey = getRecipientSelectionKey('GROUP', undefined, undefined, g.id);
            if (!selectedKeys.has(groupKey)) {
              candidates.push({
                selectionKey: groupKey,
                recipientType: 'GROUP',
                targetGroupId: +g.id,
                targetOrgCode: undefined,
                displayName: g.name,
                displayContext: `${g.orgName || g.orgCode || resourceOrg} Team`
              });
            }
          }
        });

        this.userSearchResults.set(candidates);
        this.isSearchingUsers.set(false);
      },
      error: (err: any) => {
        console.error('Failed to search recipients:', err);
        this.userSearchResults.set([]);
        this.isSearchingUsers.set(false);
      }
    });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.showUserResultsDropdown()) {
      return;
    }
    const target = event.target as Node | null;
    if (
      target &&
      this.userSearchRowRef &&
      !this.userSearchRowRef.nativeElement.contains(target)
    ) {
      this.showUserResultsDropdown.set(false);
    }
  }

  onUserSearchFocus(): void {
    if (this.userSearchQuery().trim().length > 0) {
      this.showUserResultsDropdown.set(true);
    }
  }

  onUserSearchEnter(e: Event): void {
    e.preventDefault();
    this.addRecipientFromTypedInput();
  }

  addRecipientFromSearch(recipient: SelectedRecipient): void {
    if (this.isPersonalResource() && recipient.recipientType !== 'USER_PERSONAL') {
      return;
    }
    if (!this.selectedRecipients().some((r) => r.selectionKey === recipient.selectionKey)) {
      this.selectedRecipients.update((list) => [...list, recipient]);
    }
    this.userSearchQuery.set('');
    this.userSearchResults.set([]);
    this.showUserResultsDropdown.set(false);
  }

  addRecipientFromTypedInput(): void {
    const query = this.userSearchQuery().trim();
    if (!query) return;

    // Check if query matches a candidate in search results (by key or email)
    const match = this.userSearchResults().find(
      (r) => r.selectionKey === query || (r.targetUserEmail || '').toLowerCase() === query.toLowerCase()
    );
    if (match) {
      if (this.isPersonalResource() && match.recipientType !== 'USER_PERSONAL') {
        return;
      }
      this.addRecipientFromSearch(match);
      return;
    }

    // Construct a recipient entry for manual input as USER_PERSONAL
    const isEmail = query.includes('@');
    const email = isEmail ? query : `${query.replace(/\s+/g, '.').toLowerCase()}@domain.com`;

    const currentUserEmail = this.authStore.currentUser()?.email?.toLowerCase();
    if (currentUserEmail && email.toLowerCase() === currentUserEmail) {
      this.userSearchQuery.set('');
      this.showUserResultsDropdown.set(false);
      return;
    }

    const key = getRecipientSelectionKey('USER_PERSONAL', email);

    if (this.selectedRecipients().some((r) => r.selectionKey === key)) {
      this.userSearchQuery.set('');
      this.showUserResultsDropdown.set(false);
      return;
    }

    const manualRecipient: SelectedRecipient = {
      selectionKey: key,
      recipientType: 'USER_PERSONAL',
      targetUserEmail: email,
      displayName: isEmail ? query.split('@')[0] : query,
      displayContext: 'Personal User'
    };

    this.selectedRecipients.update((list) => [...list, manualRecipient]);
    this.userSearchQuery.set('');
    this.userSearchResults.set([]);
    this.showUserResultsDropdown.set(false);
  }

  removeRecipient(selectionKey: string): void {
    this.selectedRecipients.update((list) =>
      list.filter((r) => r.selectionKey !== selectionKey)
    );
  }

  getPermissionLabel(level: SharePermissionLevel): string {
    switch (level) {
      case 'VIEW': return 'Can Read / View';
      case 'DOWNLOAD': return 'Can Download';
      case 'EDIT': return 'Can Edit / Manage';
      default: return 'Select Permission';
    }
  }

  getExpireUnitLabel(unit: 'HOURS' | 'DAYS' | 'NEVER' | 'DATE'): string {
    switch (unit) {
      case 'HOURS': return 'Hours';
      case 'DAYS': return 'Days';
      case 'NEVER': return 'Never';
      case 'DATE': return 'Specific Date';
      default: return 'Select Expiration';
    }
  }

  isAlreadySelected(selectionKey: string): boolean {
    return this.selectedRecipients().some((r) => r.selectionKey === selectionKey);
  }

  isRecipientSelected(selectionKey: string): boolean {
    return this.isAlreadySelected(selectionKey);
  }

  onExpireTimeUnitChange(unit: 'HOURS' | 'DAYS' | 'NEVER' | 'DATE'): void {
    this.expireTimeUnit.set(unit);
    if (unit === 'HOURS' && (!this.expireValue() || this.expireValue()! <= 0)) {
      this.expireValue.set(24);
    } else if (unit === 'DAYS' && (!this.expireValue() || this.expireValue()! <= 0)) {
      this.expireValue.set(7);
    }
  }

  incrementExpireValue(amount: number = 1): void {
    const current = this.expireValue() ?? 1;
    this.expireValue.set(Math.max(1, current + amount));
  }

  decrementExpireValue(amount: number = 1): void {
    const current = this.expireValue() ?? 1;
    if (current > 1) {
      this.expireValue.set(Math.max(1, current - amount));
    }
  }

  calculateExpiresAtISO(): string | undefined {
    const unit = this.expireTimeUnit();
    if (unit === 'NEVER') return undefined;

    if (unit === 'DATE') {
      const val = this.expireDateInput();
      if (!val) return undefined;
      return new Date(val).toISOString();
    }

    const val = this.expireValue();
    if (!val || val <= 0) return undefined;

    const msMultiplier = unit === 'DAYS' ? 86400000 : 3600000;
    return new Date(Date.now() + val * msMultiplier).toISOString();
  }

  calculateExpiresInHours(): number | undefined {
    const unit = this.expireTimeUnit();
    if (unit === 'NEVER') return undefined;

    if (unit === 'DATE') {
      const val = this.expireDateInput();
      if (!val) return undefined;
      const diffMs = new Date(val).getTime() - Date.now();
      return Math.max(1, Math.round(diffMs / 3600000));
    }

    const val = this.expireValue();
    if (!val || val <= 0) return undefined;

    return unit === 'DAYS' ? val * 24 : val;
  }

  grantCollaboratorPermission(): void {
    // If user typed something in search input without pressing add, auto add it!
    if (this.userSearchQuery().trim()) {
      this.addRecipientFromTypedInput();
    }

    const recipients = this.selectedRecipients();
    const currentItem = this.item();

    if (!currentItem || recipients.length === 0) {
      this.grantErrorMsg.set('Please select or add at least one recipient email.');
      return;
    }

    // Pre-flight check: Personal resources can only receive direct personal shares (USER_PERSONAL)
    if (this.isPersonalResource()) {
      const invalidRecipient = recipients.find(
        (r) => r.recipientType !== 'USER_PERSONAL' || !!r.targetOrgCode || !!r.targetGroupId
      );
      if (invalidRecipient) {
        this.grantErrorMsg.set('Personal resources can only receive direct personal shares (USER_PERSONAL).');
        return;
      }
    }

    this.isSubmittingGrant.set(true);
    this.grantErrorMsg.set(null);
    this.grantSuccessMsg.set(null);

    const isFolder = currentItem.type === 'folder';
    const resourceId = currentItem.data.id.toString();
    const expiresAt = this.calculateExpiresAtISO();

    if (isFolder) {
      // Grant for each recipient
      let completedCount = 0;
      let hasError = false;
      const total = recipients.length;

      recipients.forEach((recipient: SelectedRecipient) => {
        const effectiveRecipientType = this.isPersonalResource() ? 'USER_PERSONAL' : recipient.recipientType;
        const targetOrgCode =
          this.isPersonalResource() || effectiveRecipientType === 'GROUP'
            ? undefined
            : recipient.targetOrgCode;
        const targetGroupId = this.isPersonalResource() ? undefined : recipient.targetGroupId;
        const targetUserEmail =
          effectiveRecipientType === 'GROUP' || effectiveRecipientType === 'ORGANIZATION'
            ? undefined
            : recipient.targetUserEmail;

        const dto: BulkShareWithExclusionsDto = {
          folderId: +resourceId,
          targetUserEmail,
          permissionLevel: this.sharePermissionLevel(),
          recipientType: effectiveRecipientType,
          targetOrgCode,
          targetGroupId,
          excludedDocumentIds: this.excludedDocumentIds(),
          excludedSubfolderIds: this.excludedSubfolderIds(),
          expiresAt
        };

        this.permissionApiService.grantFolderShareWithExclusions(dto).subscribe({
          next: () => {
            completedCount++;
            if (completedCount === total && !hasError) {
              this.isSubmittingGrant.set(false);
              this.grantSuccessMsg.set(`Successfully granted folder access to ${recipients.length} recipient(s).`);
              this.selectedRecipients.set([]);
              this.permissionGranted.emit();
              this.onClose();
              setTimeout(() => this.grantSuccessMsg.set(null), 3000);
            }
          },
          error: (err: any) => {
            hasError = true;
            console.error('Failed to grant folder share:', err);
            this.isSubmittingGrant.set(false);
            this.grantErrorMsg.set(err?.error?.message || `Failed to grant access for ${recipient.displayName || recipient.targetUserEmail}.`);
          }
        });
      });
    } else {
      // Grant for file/document
      let completedCount = 0;
      let hasError = false;
      const total = recipients.length;

      recipients.forEach((recipient: SelectedRecipient) => {
        const effectiveRecipientType = this.isPersonalResource() ? 'USER_PERSONAL' : recipient.recipientType;
        const targetOrgCode =
          this.isPersonalResource() || effectiveRecipientType === 'GROUP'
            ? undefined
            : recipient.targetOrgCode;
        const targetGroupId = this.isPersonalResource() ? undefined : recipient.targetGroupId;
        const targetUserEmail =
          effectiveRecipientType === 'GROUP' || effectiveRecipientType === 'ORGANIZATION'
            ? undefined
            : recipient.targetUserEmail;

        this.permissionApiService.grantPermission({
          permissionType: 'ALLOW',
          permissionLevel: this.sharePermissionLevel(),
          documentId: currentItem.data.id,
          documentFileHash: currentItem.data.fileHash,
          targetUserEmail,
          recipientType: effectiveRecipientType,
          targetOrgCode,
          targetGroupId,
          expiresAt
        }).subscribe({
          next: () => {
            completedCount++;
            if (completedCount === total && !hasError) {
              this.isSubmittingGrant.set(false);
              this.grantSuccessMsg.set(`Successfully granted access to ${recipients.length} recipient(s).`);
              this.selectedRecipients.set([]);
              this.permissionGranted.emit();
              this.onClose();
              setTimeout(() => this.grantSuccessMsg.set(null), 3000);
            }
          },
          error: (err: any) => {
            hasError = true;
            console.error('Failed to grant permission:', err);
            this.isSubmittingGrant.set(false);
            this.grantErrorMsg.set(err?.error?.message || `Failed to grant permission for ${recipient.displayName || recipient.targetUserEmail}.`);
          }
        });
      });
    }
  }

  createSecureShareLink(): void {
    const currentItem = this.item();
    if (!currentItem) return;

    this.isCreatingLink.set(true);
    const isFolder = currentItem.type === 'folder';

    const req = {
      documentId: !isFolder ? +currentItem.data.id : undefined,
      folderId: isFolder ? +currentItem.data.id : undefined,
      permissionLevel: this.sharePermissionLevel(),
      password: this.shareLinkPassword().trim() || undefined,
      expiresInHours: this.calculateExpiresInHours()
    };

    this.shareApiService.createShareLink(req).subscribe({
      next: (res: ShareLinkResponseDto) => {
        this.isCreatingLink.set(false);
        this.shareLinkCreated.emit(res);
        this.permissionGranted.emit();
        this.onClose();
      },
      error: (err: any) => {
        console.error('Error creating share link:', err);
        this.isCreatingLink.set(false);
        this.grantErrorMsg.set('Failed to generate secure share link.');
      }
    });
  }

  copyShareLinkUrl(token: string): void {
    const fullUrl = this.getShareLinkFullUrl(token);
    navigator.clipboard.writeText(fullUrl);
    this.copiedShareToken.set(token);
    setTimeout(() => this.copiedShareToken.set(null), 2000);
  }

  getShareLinkFullUrl(token: string): string {
    return `${window.location.origin}/share/${token}`;
  }

  // Folder Content Exclusions Methods
  loadFolderContents(folderId: number): void {
    this.isLoadingFolderContents.set(true);
    this.folderApiService.getFolderContents(folderId).subscribe({
      next: (contents: FolderContentResponseDto) => {
        this.folderContents.set(contents);
        this.isLoadingFolderContents.set(false);
      },
      error: (err: any) => {
        console.error('Failed to load folder contents for exclusions:', err);
        this.folderContents.set(null);
        this.isLoadingFolderContents.set(false);
      }
    });
  }

  toggleSubfolderExclusion(id: number): void {
    const list = [...this.excludedSubfolderIds()];
    const index = list.indexOf(id);
    if (index > -1) {
      list.splice(index, 1);
    } else {
      list.push(id);
    }
    this.excludedSubfolderIds.set(list);
  }

  toggleDocumentExclusion(id: number): void {
    const list = [...this.excludedDocumentIds()];
    const index = list.indexOf(id);
    if (index > -1) {
      list.splice(index, 1);
    } else {
      list.push(id);
    }
    this.excludedDocumentIds.set(list);
  }

  isSubfolderExcluded(id: number): boolean {
    return this.excludedSubfolderIds().includes(id);
  }

  isDocumentExcluded(id: number): boolean {
    return this.excludedDocumentIds().includes(id);
  }

  getExcludedTotalCount(): number {
    return this.excludedSubfolderIds().length + this.excludedDocumentIds().length;
  }

  getFilteredSubfolders() {
    const sfs = this.folderContents()?.subFolders || [];
    const q = this.exclusionSearchQuery().toLowerCase().trim();
    if (!q) return sfs;
    return sfs.filter((sf: any) => sf.name.toLowerCase().includes(q));
  }

  getFilteredDocuments() {
    const docs = this.folderContents()?.documents || [];
    const q = this.exclusionSearchQuery().toLowerCase().trim();
    if (!q) return docs;
    return docs.filter((d: any) => (d.name || d.fileName || '').toLowerCase().includes(q));
  }

  selectAllExclusions(): void {
    const contents = this.folderContents();
    if (!contents) return;
    const subfolderIds = (contents.subFolders || []).map((sf: any) => sf.id);
    const documentIds = (contents.documents || []).map((doc: any) => +doc.id);
    this.excludedSubfolderIds.set(subfolderIds);
    this.excludedDocumentIds.set(documentIds);
  }

  clearExclusions(): void {
    this.excludedSubfolderIds.set([]);
    this.excludedDocumentIds.set([]);
  }

  onBackdropClick(e: MouseEvent): void {
    this.onClose();
  }

  onClose(): void {
    if (!this.isSubmittingGrant() && !this.isCreatingLink()) {
      this.closed.emit();
    }
  }
}
