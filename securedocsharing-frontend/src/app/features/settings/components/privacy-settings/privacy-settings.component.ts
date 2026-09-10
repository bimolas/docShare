import { Component, OnInit, inject, signal, computed, effect, ChangeDetectionStrategy } from '@angular/core';

import { FormsModule } from '@angular/forms';
import { SettingsApiService } from '../../services/settings-api.service';
import {
  UserMessagePrivacySettingsDto,
  WorkspacePolicyDto,
  WorkspacePolicyTarget,
  ExternalSharingMode,
  DiscoverabilityMode,
  ReviewerScopePolicy
} from '../../interfaces/settings.interfaces';
import { WorkspaceContextStore } from '../../../../state/workspace/workspace-context.store';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { LucideUser } from '@lucide/angular';
import { forkJoin, of } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';

@Component({
  selector: 'app-privacy-settings',
  standalone: true,
  imports: [
    FormsModule,
    IconComponent,
    LucideUser
],
  template: `
    <div class="settings-section">
      <div class="settings-header">
        <h2>Privacy & Policies</h2>
        <p>Manage direct messages, visibility, and workspace sharing policies.</p>
      </div>
    
      <!-- Loading State -->
      @if (isLoadingInit()) {
        @for (_ of [1, 2]; track _) {
          <div class="skeleton-card">
            <div class="skeleton-header"></div>
            @for (i of [1, 2, 3]; track i) {
              <div class="skeleton-item">
                <div class="skeleton-text">
                  <div class="skeleton-title"></div>
                  <div class="skeleton-desc"></div>
                </div>
                <div class="skeleton-control"></div>
              </div>
            }
          </div>
        }
      } @else {
        <!-- Workspace Policies Section (Only if in a workspace context) -->
        @if (activePolicyTarget(); as target) {
          <div class="settings-card workspace-card">
            <div class="card-header">
              <div class="card-icon-wrapper">
                <app-icon
                  [name]="workspaceContext.isOrganization() ? 'building-2' : 'user-round'"
                  [size]="18"
                  class="card-icon"
                ></app-icon>
              </div>
              <div class="card-title">
                <h3>Workspace Policies</h3>
                <p>
                  Configure sharing and discoverability for
                  <strong>{{ contextDisplayName() }}</strong>
                </p>
              </div>
            </div>
            @if (isLoadingPolicy()) {
              <div class="setting-group">
                @for (i of [1, 2, 3]; track i) {
                  <div class="skeleton-item">
                    <div class="skeleton-text">
                      <div class="skeleton-title"></div>
                      <div class="skeleton-desc"></div>
                    </div>
                    <div class="skeleton-control"></div>
                  </div>
                }
              </div>
            } @else if (policyError()) {
              <div class="empty-state">
                <p>{{ policyError() }}</p>
              </div>
            } @else if (workspacePolicy()) {
              <div class="setting-group">
                <!-- Discoverability Mode -->
                <div class="setting-item">
                  <div class="setting-info">
                    <label for="discoverability-mode">Discoverability Mode</label>
                    <p>Control who can find this workspace in search and directories.</p>
                  </div>
                  <div class="setting-action select-wrapper">
                    <select
                      id="discoverability-mode"
                      [ngModel]="workspacePolicy()?.discoverabilityMode"
                      (ngModelChange)="updateWorkspacePolicy('discoverabilityMode', $event)"
                      [disabled]="isSavingWs('discoverabilityMode')"
                      >
                      <option value="PRIVATE">Private (Invite Only)</option>
                      <option value="TRUSTED_ONLY">Trusted Network</option>
                      <option value="DISCOVERABLE">Fully Discoverable</option>
                    </select>
                    <app-icon
                      name="chevron-down"
                      [size]="14"
                      class="select-chevron"
                    ></app-icon>
                  </div>
                </div>
                <!-- External Sharing Mode -->
                <div class="setting-item">
                  <div class="setting-info">
                    <label for="external-sharing">External Sharing</label>
                    <p>Allow members to share documents outside the workspace.</p>
                  </div>
                  <div class="setting-action">
                    <label
                      class="toggle-switch"
                      [class.is-loading]="isSavingWs('externalSharingMode')"
                      >
                      <input
                        type="checkbox"
                        id="external-sharing"
                        [ngModel]="workspacePolicy()?.externalSharingMode === 'ON'"
                        (ngModelChange)="updateWorkspacePolicy('externalSharingMode', $event ? 'ON' : 'OFF')"
                        [disabled]="isSavingWs('externalSharingMode')"
                        />
                      <span class="slider"></span>
                    </label>
                  </div>
                </div>
                <!-- Access Requests -->
                <div class="setting-item">
                  <div class="setting-info">
                    <label for="allow-access">Allow Access Requests</label>
                    <p>Let non-members request access to this workspace.</p>
                  </div>
                  <div class="setting-action">
                    <label
                      class="toggle-switch"
                      [class.is-loading]="isSavingWs('allowAccessRequests')"
                      >
                      <input
                        type="checkbox"
                        id="allow-access"
                        [ngModel]="workspacePolicy()?.allowAccessRequests"
                        (ngModelChange)="updateWorkspacePolicy('allowAccessRequests', $event)"
                        [disabled]="isSavingWs('allowAccessRequests')"
                        />
                      <span class="slider"></span>
                    </label>
                  </div>
                </div>
                <!-- Request Reason -->
                <div
                  class="setting-item"
                  [class.disabled-item]="!workspacePolicy()?.allowAccessRequests"
                  >
                  <div class="setting-info">
                    <label for="require-reason">Require Request Reason</label>
                    <p>Users must provide a justification when requesting access.</p>
                  </div>
                  <div class="setting-action">
                    <label
                      class="toggle-switch"
                      [class.is-loading]="isSavingWs('requireRequestReason')"
                      >
                      <input
                        type="checkbox"
                        id="require-reason"
                        [ngModel]="workspacePolicy()?.requireRequestReason"
                        (ngModelChange)="updateWorkspacePolicy('requireRequestReason', $event)"
                    [disabled]="
                      isSavingWs('requireRequestReason') ||
                      !workspacePolicy()?.allowAccessRequests
                    "
                        />
                      <span class="slider"></span>
                    </label>
                  </div>
                </div>
                <!-- External Access Requests -->
                <div
                  class="setting-item"
                  [class.disabled-item]="!workspacePolicy()?.allowAccessRequests"
                  >
                  <div class="setting-info">
                    <label for="external-access">Allow External Access Requests</label>
                    <p>Allow users outside this workspace to request access.</p>
                  </div>
                  <div class="setting-action">
                    <label
                      class="toggle-switch"
                      [class.is-loading]="isSavingWs('allowExternalAccessRequests')"
                      >
                      <input
                        type="checkbox"
                        id="external-access"
                        [ngModel]="workspacePolicy()?.allowExternalAccessRequests"
                        (ngModelChange)="updateWorkspacePolicy('allowExternalAccessRequests', $event)"
                    [disabled]="
                      isSavingWs('allowExternalAccessRequests') ||
                      !workspacePolicy()?.allowAccessRequests
                    "
                        />
                      <span class="slider"></span>
                    </label>
                  </div>
                </div>
              </div>
            } @else {
              <div class="empty-state">
                <p>
                  Could not load workspace policies. You may not have permission to
                  view them.
                </p>
              </div>
            }
          </div>
        }
        <!-- Personal Privacy Settings -->
        <div class="settings-card personal-card">
          <div class="card-header">
            <div class="card-icon-wrapper personal">
              <svg
                lucideUser
                width="18"
                height="18"
                class="card-icon"
                aria-hidden="true"
              ></svg>
            </div>
            <div class="card-title">
              <h3>Personal Privacy</h3>
              <p>Account-wide messaging and interaction preferences.</p>
            </div>
          </div>
          @if (personalSettings()) {
            <div class="setting-group">
              <div class="setting-item">
                <div class="setting-info">
                  <label for="internal-dms">Allow Internal Direct Messages</label>
                  <p>
                    Receive direct messages from people within your shared
                    organizations.
                  </p>
                </div>
                <div class="setting-action">
                  <label
                    class="toggle-switch"
                    [class.is-loading]="isSavingPersonal('allowInternalDms')"
                    >
                    <input
                      type="checkbox"
                      id="internal-dms"
                      [ngModel]="personalSettings()?.allowInternalDms"
                      (ngModelChange)="updatePersonalSetting('allowInternalDms', $event)"
                      [disabled]="isSavingPersonal('allowInternalDms')"
                      />
                    <span class="slider"></span>
                  </label>
                </div>
              </div>
              <div class="setting-item">
                <div class="setting-info">
                  <label for="external-dms">Allow External Direct Messages</label>
                  <p>
                    Receive direct messages from anyone using SecureDocSharing.
                  </p>
                </div>
                <div class="setting-action">
                  <label
                    class="toggle-switch"
                    [class.is-loading]="isSavingPersonal('allowExternalDms')"
                    >
                    <input
                      type="checkbox"
                      id="external-dms"
                      [ngModel]="personalSettings()?.allowExternalDms"
                      (ngModelChange)="updatePersonalSetting('allowExternalDms', $event)"
                      [disabled]="isSavingPersonal('allowExternalDms')"
                      />
                    <span class="slider"></span>
                  </label>
                </div>
              </div>
              <div class="setting-item">
                <div class="setting-info">
                  <label for="read-receipts">Read Receipts</label>
                  <p>Let others know when you have read their messages.</p>
                </div>
                <div class="setting-action">
                  <label
                    class="toggle-switch"
                    [class.is-loading]="isSavingPersonal('readReceiptsEnabled')"
                    >
                    <input
                      type="checkbox"
                      id="read-receipts"
                      [ngModel]="personalSettings()?.readReceiptsEnabled"
                      (ngModelChange)="updatePersonalSetting('readReceiptsEnabled', $event)"
                      [disabled]="isSavingPersonal('readReceiptsEnabled')"
                      />
                    <span class="slider"></span>
                  </label>
                </div>
              </div>
            </div>
          }
        </div>
      }
    
    </div>
    `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [`
    .settings-section {
      max-width: 800px;
      margin: 0 auto;
      animation: fadeIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    }

    .settings-header {
      margin-bottom: 2.5rem;
    }

    .settings-header h2 {
      font-size: 1.75rem;
      font-weight: 600;
      color: #09090b;
      margin: 0 0 0.5rem 0;
      letter-spacing: -0.03em;
    }

    .settings-header p {
      font-size: 0.95rem;
      color: #71717a;
      margin: 0;
    }

    .settings-card {
      background: #ffffff;
      border: 1px solid #e4e4e7;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
      margin-bottom: 2rem;
      transition: box-shadow 0.2s ease;
    }

    .settings-card:hover {
      box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.04);
    }

    .card-header {
      display: flex;
      align-items: flex-start;
      gap: 1rem;
      padding: 1.5rem 1.5rem 1rem;
      border-bottom: 1px solid #f4f4f5;
      background: #fafafa;
    }

    .card-icon-wrapper {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      border-radius: 8px;
      background: rgba(249, 115, 22, 0.1);
      color: #ea580c;
      flex-shrink: 0;
    }

    .card-icon-wrapper.personal {
      background: rgba(14, 165, 233, 0.1);
      color: #0284c7;
    }

    .card-title {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .card-title h3 {
      font-size: 1.1rem;
      font-weight: 600;
      color: #18181b;
      margin: 0;
      letter-spacing: -0.01em;
    }

    .card-title p {
      font-size: 0.875rem;
      color: #71717a;
      margin: 0;
      line-height: 1.4;
    }

    .setting-group {
      display: flex;
      flex-direction: column;
    }

    .setting-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1.5rem;
      gap: 2rem;
      border-bottom: 1px solid #f4f4f5;
      transition: background-color 0.15s ease;
    }

    .setting-item:hover {
      background-color: #fafafa;
    }

    .setting-item:last-child {
      border-bottom: none;
    }

    .setting-item.disabled-item {
      opacity: 0.5;
      pointer-events: none;
    }

    .setting-info {
      flex: 1;
    }

    .setting-info label {
      display: block;
      font-size: 0.95rem;
      font-weight: 500;
      color: #27272a;
      margin-bottom: 0.375rem;
      cursor: pointer;
    }

    .setting-info p {
      font-size: 0.875rem;
      color: #71717a;
      margin: 0;
      line-height: 1.4;
    }

    .empty-state {
      padding: 2rem;
      text-align: center;
      color: #71717a;
      font-size: 0.9rem;
    }

    /* Select Styles */
    .select-wrapper {
      position: relative;
      width: 200px;
    }

    .select-wrapper select {
      width: 100%;
      appearance: none;
      background: #ffffff;
      border: 1px solid #e4e4e7;
      border-radius: 6px;
      padding: 0.5rem 2rem 0.5rem 0.75rem;
      font-size: 0.875rem;
      font-weight: 500;
      color: #27272a;
      cursor: pointer;
      outline: none;
      transition: all 0.2s ease;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
    }

    .select-wrapper select:hover {
      border-color: #d4d4d8;
    }

    .select-wrapper select:focus {
      border-color: #f97316;
      box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.1);
    }

    .select-wrapper select:disabled {
      background: #f4f4f5;
      cursor: not-allowed;
      opacity: 0.7;
    }

    .select-chevron {
      position: absolute;
      right: 0.75rem;
      top: 50%;
      transform: translateY(-50%);
      pointer-events: none;
      color: #71717a;
    }

    /* Toggle Switch Styles */
    .toggle-switch {
      position: relative;
      display: inline-block;
      width: 44px;
      height: 24px;
      flex-shrink: 0;
    }

    .toggle-switch input {
      opacity: 0;
      width: 0;
      height: 0;
    }

    .slider {
      position: absolute;
      cursor: pointer;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: #e4e4e7;
      transition: .3s cubic-bezier(0.16, 1, 0.3, 1);
      border-radius: 24px;
    }

    .slider:before {
      position: absolute;
      content: "";
      height: 20px;
      width: 20px;
      left: 2px;
      bottom: 2px;
      background-color: white;
      transition: .3s cubic-bezier(0.16, 1, 0.3, 1);
      border-radius: 50%;
      box-shadow: 0 1px 2px rgba(0,0,0,0.1), 0 1px 1px rgba(0,0,0,0.06);
    }

    input:checked + .slider {
      background-color: #f97316;
    }

    input:focus-visible + .slider {
      outline: 2px solid #f97316;
      outline-offset: 2px;
    }

    input:checked + .slider:before {
      transform: translateX(20px);
    }

    input:disabled + .slider {
      opacity: 0.5;
      cursor: not-allowed;
    }

    /* Loading state for toggle */
    .toggle-switch.is-loading .slider {
      opacity: 0.7;
      pointer-events: none;
    }

    /* Skeleton Loading */
    .skeleton-card {
      background: #ffffff;
      border: 1px solid #e4e4e7;
      border-radius: 12px;
      overflow: hidden;
      margin-bottom: 2rem;
    }

    .skeleton-header {
      height: 88px;
      background: #fafafa;
      border-bottom: 1px solid #f4f4f5;
    }

    .skeleton-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1.5rem;
      gap: 2rem;
      border-bottom: 1px solid #f4f4f5;
    }

    .skeleton-text {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }

    .skeleton-title {
      height: 1.125rem;
      width: 30%;
      background: #f4f4f5;
      border-radius: 4px;
      animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
    }

    .skeleton-desc {
      height: 0.875rem;
      width: 60%;
      background: #f4f4f5;
      border-radius: 4px;
      animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
    }

    .skeleton-control {
      width: 44px;
      height: 24px;
      background: #f4f4f5;
      border-radius: 24px;
      animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.5; }
    }

    @keyframes fadeIn {
      from {
        opacity: 0;
        transform: translateY(8px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }
  `]
})
export class PrivacySettingsComponent implements OnInit {
  private readonly settingsApi = inject(SettingsApiService);
  readonly workspaceContext = inject(WorkspaceContextStore);

  readonly activePolicyTarget = computed<WorkspacePolicyTarget | null>(() => {
    if (this.workspaceContext.isPersonal()) {
      return 'personal';
    }
    return this.workspaceContext.activeOrgCode() 
      || this.workspaceContext.activeWorkspace()?.organizationCode 
      || this.workspaceContext.activeWorkspace()?.orgCode 
      || null;
  });

  readonly contextDisplayName = computed<string>(() => {
    if (this.workspaceContext.isPersonal()) {
      return this.workspaceContext.activeWorkspace()?.name || 'Personal Space';
    }
    return this.workspaceContext.activeOrganization()?.name 
      || this.workspaceContext.activeWorkspace()?.name 
      || 'Organization';
  });

  personalSettings = signal<UserMessagePrivacySettingsDto | null>(null);
  workspacePolicy = signal<WorkspacePolicyDto | null>(null);

  isLoadingInit = signal<boolean>(true);
  isLoadingPolicy = signal<boolean>(false);
  policyError = signal<string | null>(null);

  savingPersonalStates = signal<Record<string, boolean>>({});
  savingWsStates = signal<Record<string, boolean>>({});

  private currentTarget: WorkspacePolicyTarget | null = null;

  constructor() {
    // Reactively reload workspace policies if policy target changes (e.g. switching orgs or switching to personal)
    effect(() => {
      const target = this.activePolicyTarget();

      if (target && !this.isLoadingInit() && target !== this.currentTarget) {
        this.currentTarget = target;
        this.loadWorkspacePolicy(target);
      }
    }, { allowSignalWrites: true });
  }

  ngOnInit() {
    this.loadSettings();
  }

  private loadSettings() {
    this.isLoadingInit.set(true);
    this.policyError.set(null);

    const target = this.activePolicyTarget();
    this.currentTarget = target;

    const requests = {
      personal: this.settingsApi.getPrivacySettings().pipe(
        catchError(err => {
          console.error('Failed to load privacy settings', err);

          return of({
            allowInternalDms: true,
            allowExternalDms: true,
            readReceiptsEnabled: true
          } as UserMessagePrivacySettingsDto);
        })
      ),

      workspace: target
        ? this.settingsApi.getWorkspacePolicy(target).pipe(
            catchError(err => {
              console.error('Failed to load workspace policy', err);
              this.policyError.set('Could not load workspace policies. You may not have permission to view them.');
              return of(null);
            })
          )
        : of(null)
    };

    forkJoin(requests)
      .pipe(
        finalize(() => this.isLoadingInit.set(false))
      )
      .subscribe(res => {
        this.personalSettings.set(res.personal);
        this.workspacePolicy.set(res.workspace);
      });
  }

  private loadWorkspacePolicy(target: WorkspacePolicyTarget) {
    this.isLoadingPolicy.set(true);
    this.policyError.set(null);

    this.settingsApi.getWorkspacePolicy(target).pipe(
      finalize(() => this.isLoadingPolicy.set(false))
    ).subscribe({
      next: (policy) => {
        this.workspacePolicy.set(policy);
        this.policyError.set(null);
      },
      error: (err) => {
        console.error('Failed to load workspace policy', err);
        this.workspacePolicy.set(null);
        this.policyError.set('Could not load workspace policies. You may not have permission to view them.');
      }
    });
  }

  // --- Personal Settings Updates ---
  isSavingPersonal(key: keyof UserMessagePrivacySettingsDto): boolean {
    return !!this.savingPersonalStates()[key];
  }

  updatePersonalSetting(
    key: keyof UserMessagePrivacySettingsDto,
    newValue: boolean
  ) {
    const currentSettings = this.personalSettings();

    if (!currentSettings) {
      return;
    }

    const updatedSettings = {
      ...currentSettings,
      [key]: newValue
    };

    this.personalSettings.set(updatedSettings); // Optimistic UI update

    this.savingPersonalStates.update(s => ({
      ...s,
      [key]: true
    }));

    this.settingsApi.updatePrivacySettings(updatedSettings).subscribe({
      next: (res) => {
        this.personalSettings.set(res);

        this.savingPersonalStates.update(s => ({
          ...s,
          [key]: false
        }));
      },

      error: (err) => {
        console.error('Failed to update personal setting', err);

        this.personalSettings.set(currentSettings);

        this.savingPersonalStates.update(s => ({
          ...s,
          [key]: false
        }));
      }
    });
  }

  // --- Workspace Policy Updates ---
  isSavingWs(key: keyof WorkspacePolicyDto): boolean {
    return !!this.savingWsStates()[key];
  }

  updateWorkspacePolicy(
    key: keyof WorkspacePolicyDto,
    newValue: any
  ) {
    const currentPolicy = this.workspacePolicy();
    const target = this.activePolicyTarget();

    if (!currentPolicy || !target) {
      return;
    }

    const updatedPolicy = {
      ...currentPolicy,
      [key]: newValue
    };

    this.workspacePolicy.set(updatedPolicy); // Optimistic UI update

    this.savingWsStates.update(s => ({
      ...s,
      [key]: true
    }));

    this.settingsApi
      .updateWorkspacePolicy(target, updatedPolicy)
      .subscribe({
        next: (res) => {
          this.workspacePolicy.set(res);

          this.savingWsStates.update(s => ({
            ...s,
            [key]: false
          }));
        },

        error: (err) => {
          console.error('Failed to update workspace policy', err);

          this.workspacePolicy.set(currentPolicy);

          this.savingWsStates.update(s => ({
            ...s,
            [key]: false
          }));
        }
      });
  }
}