import {
  Component,
  ChangeDetectionStrategy,
  signal,
  computed,
  inject,
  effect,
  OnInit,
  OnDestroy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Subscription, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  LucideUsersRound,
  LucideRefreshCw,
  LucidePlus,
  LucideAlertCircle,
} from '@lucide/angular';
import { OrganizationApiService } from '../../../core/services/organization-api.service';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { AuthStore } from '../../../state/auth/auth.store';
import { NotificationService } from '../../../core/services/notification.service';
import { GroupResponseDto, OrganizationMemberResponseDto } from '../../../core/models/organization.model';
import { TeamDelegationService } from '../services/team-delegation.service';
import { TeamAccessBoundaryDto } from '../models/team-delegation.models';
import {
  TeamsToolbarComponent,
  TeamStatusFilter,
} from '../components/teams-toolbar/teams-toolbar.component';
import { TeamCardComponent } from '../components/team-card/team-card.component';
import { TeamsEmptyStateComponent } from '../components/empty-state/teams-empty-state.component';
import { CreateTeamModalComponent } from '../components/create-team-modal/create-team-modal.component';
import { EditTeamModalComponent } from '../components/edit-team-modal/edit-team-modal.component';
import {
  DeleteTeamModalComponent,
  DeleteModalMode,
} from '../components/delete-team-modal/delete-team-modal.component';
import { TeamDetailsDrawerComponent } from '../components/team-details-drawer/team-details-drawer.component';
import { hasOrganizationCapability } from '../../../core/models/organization-capability.model';

/**
 * Organization Teams List Page Component.
 * Orchestrates team search, status filter, responsive card grid, team lifecycle actions, and modals.
 * Visual language matches the Members page: white header, cloudy-border surfaces, orange accent.
 */
@Component({
  selector: 'app-teams-page',
  standalone: true,
  imports: [
    CommonModule,
    LucideUsersRound,
    LucideRefreshCw,
    LucidePlus,
    LucideAlertCircle,
    TeamsToolbarComponent,
    TeamCardComponent,
    TeamsEmptyStateComponent,
    CreateTeamModalComponent,
    EditTeamModalComponent,
    DeleteTeamModalComponent,
    TeamDetailsDrawerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="teams-page-layout">
      <!-- Page Header -->
      <header class="teams-page-header">
        <div class="header-main-row">
          <div class="header-titles">
            <div class="title-with-icon">
              <div class="header-icon-badge">
                <svg lucideUsersRound class="header-lucide-icon"></svg>
              </div>
              <div>
                <h1>Organization Teams</h1>
                <p class="subtitle">
                  Browse and manage team groups, member allocations, and workspace access.
                </p>
              </div>
            </div>
          </div>

          <div class="header-actions">
            @if (canCreateTeams()) {
              <button
                type="button"
                class="btn-create"
                (click)="onCreateTeamClicked()"
                title="Create Team"
              >
                <svg lucidePlus class="btn-icon"></svg>
                <span>Create Team</span>
              </button>
            }
          </div>
        </div>
      </header>

      <!-- Main Container -->
      <div class="teams-container">
        <!-- Toolbar: search + filter tabs + refresh -->
        <sd-teams-toolbar
          [searchQuery]="searchQuery()"
          [activeFilter]="selectedStatus()"
          [allCount]="totalCount()"
          [activeCount]="activeCount()"
          [archivedCount]="archivedCount()"
          [isLoading]="isLoading()"
          (searchChange)="onSearchChange($event)"
          (filterChange)="onStatusChange($event)"
          (refresh)="loadTeams()"
        />

        <!-- Content Surface -->
        <main class="teams-content-surface">

          <!-- Loading State -->
          @if (isLoading() && allTeams().length === 0) {
            <div class="state-container loading-state">
              <div class="spinner"></div>
              <p>Loading organization teams...</p>
            </div>
          }

          <!-- Error State -->
          @else if (error()) {
            <div class="state-container error-state">
              <div class="state-icon-box error-box">
                <svg lucideAlertCircle class="state-icon"></svg>
              </div>
              <h3>Failed to load teams</h3>
              <p>{{ error() }}</p>
              <button type="button" class="btn-retry" (click)="loadTeams()">
                <svg lucideRefreshCw class="btn-icon"></svg>
                Retry
              </button>
            </div>
          }

          <!-- Empty State -->
          @else if (filteredTeams().length === 0) {
            <sd-teams-empty-state
              [isFiltered]="isFiltered()"
              [canCreate]="canCreateTeams()"
              (clearFilters)="onClearFilters()"
              (createTeam)="onCreateTeamClicked()"
            />
          }

          <!-- Team Enterprise Table -->
          @else {
            <div class="teams-table-container">
              <div class="teams-list-header">
                <div class="header-col col-team">Team</div>
                <div class="header-col col-my-role">My Role</div>
                <div class="header-col col-manager">Team Manager</div>
                <div class="header-col col-workspace">Workspace Access</div>
                <div class="header-col col-members">Members</div>
                <div class="header-col col-action">Actions</div>
              </div>

              <div class="teams-list">
                @for (team of filteredTeams(); track team.id) {
                  <sd-team-card
                    [group]="team"
                    [boundary]="teamBoundaries()[team.id] || null"
                    [manager]="teamManagers()[team.id] || null"
                    [myRole]="teamMyRoles()[team.id] || '—'"
                    [grantsCount]="teamGrants()[team.id] || 0"
                    [canManage]="canCreateTeams()"
                    (selectTeam)="onTeamCardClicked($event)"
                    (editTeam)="onEditTeam($event)"
                    (archiveTeam)="onArchiveTeam($event)"
                    (unarchiveTeam)="onUnarchiveTeam($event)"
                    (deleteTeam)="onDeleteTeam($event)"
                  />
                }
              </div>
            </div>

            <!-- Footer count -->
            <footer class="teams-footer">
              <span class="footer-count">
                Showing {{ filteredTeams().length }} of {{ totalCount() }} team{{
                  totalCount() === 1 ? '' : 's'
                }}
              </span>
            </footer>
          }
        </main>
      </div>

      <!-- Create Team Modal -->
      @if (isCreateModalOpen()) {
        <sd-create-team-modal
          [isOpen]="isCreateModalOpen()"
          [orgCode]="orgCode()"
          (closed)="isCreateModalOpen.set(false)"
          (teamCreated)="onTeamCreated($event)"
        />
      }

      <!-- Edit Team Modal -->
      @if (isEditModalOpen()) {
        <sd-edit-team-modal
          [isOpen]="isEditModalOpen()"
          [orgCode]="orgCode()"
          [team]="teamToEdit()"
          (closed)="onEditModalClosed()"
          (teamUpdated)="onTeamUpdated($event)"
        />
      }

      <!-- Delete / Archive Modal -->
      @if (isDeleteModalOpen()) {
        <sd-delete-team-modal
          [isOpen]="isDeleteModalOpen()"
          [orgCode]="orgCode()"
          [team]="teamToDelete()"
          [initialMode]="deleteModalMode()"
          (closed)="onDeleteModalClosed()"
          (teamActionCompleted)="onTeamActionCompleted($event)"
        />
      }

      <!-- Team Details Drawer -->
      @if (isDetailsDrawerOpen()) {
        <app-team-details-drawer
          [isOpen]="isDetailsDrawerOpen()"
          [orgCode]="orgCode()"
          [team]="selectedTeam()"
          (closeDrawer)="onDetailsDrawerClosed()"
          (teamUpdated)="loadTeams()"
        />
      }
    </div>
  `,
  styles: [
    `
      .teams-page-layout {
        display: flex;
        flex-direction: column;
        min-height: calc(100vh - 64px);
        background-color: #fafafa;
      }

      /* ── Header ─────────────────────────────────── */
      .teams-page-header {
        background: #ffffff;
        border-bottom: 1px solid #e4e4e7;
        padding: 2rem 0;
      }

      .header-main-row {
        max-width: 1440px;
        margin: 0 auto;
        padding: 0 2rem;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1.5rem;
      }

      .title-with-icon {
        display: flex;
        align-items: flex-start;
        gap: 1rem;
      }

      .header-icon-badge {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 44px;
        height: 44px;
        border-radius: 12px;
        background: #fff7ed;
        color: #ea580c;
        border: 1px solid #fed7aa;
        flex-shrink: 0;
      }

      .header-lucide-icon {
        width: 22px;
        height: 22px;
      }

      .header-titles h1 {
        font-size: 1.5rem;
        font-weight: 600;
        color: #09090b;
        margin: 0 0 0.25rem 0;
        letter-spacing: -0.025em;
      }

      .subtitle {
        font-size: 0.9rem;
        color: #71717a;
        margin: 0;
      }

      .header-actions {
        display: flex;
        align-items: center;
        gap: 0.875rem;
      }

      /* ── Create Button ───────────────────────────── */
      .btn-create {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 1rem;
        background: #f97316;
        color: #ffffff;
        border: none;
        border-radius: 8px;
        font-size: 0.875rem;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
        box-shadow: 0 1px 2px rgba(249, 115, 22, 0.2);

        &:hover {
          background: #ea580c;
          transform: translateY(-1px);
        }

        &:active {
          transform: translateY(0);
        }
      }

      .btn-icon {
        width: 16px;
        height: 16px;
      }

      /* ── Container ──────────────────────────────── */
      .teams-container {
        max-width: 1440px;
        margin: 0 auto;
        width: 100%;
        padding: 2rem;
        display: flex;
        flex-direction: column;
        gap: 1.25rem;
      }

      /* ── Content Surface ────────────────────────── */
      .teams-content-surface {
        background: #ffffff;
        border-radius: 12px;
        border: 1px solid #e4e4e7;
        box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.04);
        overflow: hidden;
      }

      /* ── Teams Table & Header ───────────────────── */
      .teams-table-container {
        width: 100%;
        overflow-x: auto;
        -webkit-overflow-scrolling: touch;
      }

      .teams-list-header {
        display: grid;
        grid-template-columns: minmax(200px, 1.3fr) 110px minmax(160px, 1fr) minmax(200px, 1.2fr) 90px 140px;
        align-items: center;
        gap: 1.25rem;
        padding: 0.75rem 1.5rem;
        background-color: #f8fafc;
        border-bottom: 1px solid #e2e8f0;
        font-size: 0.75rem;
        font-weight: 600;
        color: #64748b;
        text-transform: uppercase;
        letter-spacing: 0.05em;
      }

      .header-col {
        min-width: 0;
      }

      .col-action {
        text-align: right;
      }

      .teams-list {
        display: flex;
        flex-direction: column;
      }

      @media (max-width: 860px) {
        .teams-list-header {
          display: none;
        }

        .teams-table-container {
          overflow-x: visible;
        }
      }

      /* ── Footer ─────────────────────────────────── */
      .teams-footer {
        padding: 0.875rem 1.25rem;
        background: #f8fafc;
        border-top: 1px solid #e4e4e7;
        font-size: 0.8125rem;
        color: #71717a;
      }

      /* ── State Containers ────────────────────────── */
      .state-container {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 4.5rem 2rem;
        text-align: center;

        h3 {
          font-size: 1.125rem;
          font-weight: 600;
          color: #18181b;
          margin: 0 0 0.375rem 0;
        }

        p {
          font-size: 0.875rem;
          color: #71717a;
          margin: 0;
          max-width: 360px;
          line-height: 1.5;
        }
      }

      .loading-state {
        .spinner {
          width: 32px;
          height: 32px;
          border: 3px solid #e4e4e7;
          border-top-color: #f97316;
          border-radius: 50%;
          animation: spin 0.8s linear infinite;
          margin-bottom: 1rem;
        }
      }

      .state-icon-box {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 56px;
        height: 56px;
        border-radius: 50%;
        background: #f4f4f5;
        color: #a1a1aa;
        margin-bottom: 1.25rem;

        &.error-box {
          background: #fef2f2;
          color: #ef4444;
        }
      }

      .state-icon {
        width: 26px;
        height: 26px;
      }

      .btn-retry {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        margin-top: 1.25rem;
        padding: 0.5rem 1rem;
        font-size: 0.875rem;
        font-weight: 500;
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.15s ease;
        background: #ffffff;
        border: 1px solid #fecaca;
        color: #dc2626;

        &:hover {
          background: #fef2f2;
        }
      }

      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }

      @media (max-width: 768px) {
        .teams-container {
          padding: 1rem;
        }

        .header-main-row {
          flex-direction: column;
          align-items: flex-start;
        }

        .col-date {
          display: none;
        }

        .col-members {
          width: auto;
        }
      }
    `,
  ],
})
export class TeamsPageComponent implements OnInit, OnDestroy {
  private readonly organizationApiService = inject(OrganizationApiService);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);
  private readonly authStore = inject(AuthStore);
  private readonly notificationService = inject(NotificationService);
  private readonly teamDelegationService = inject(TeamDelegationService);
  private readonly route = inject(ActivatedRoute);

  // State Signals
  readonly allTeams = signal<GroupResponseDto[]>([]);
  readonly isLoading = signal<boolean>(true);
  readonly error = signal<string | null>(null);
  readonly searchQuery = signal<string>('');
  readonly selectedStatus = signal<TeamStatusFilter>('ALL');

  // Enriched Team Governance Metadata Signals
  readonly teamBoundaries = signal<Record<number, TeamAccessBoundaryDto | null>>({});
  readonly teamManagers = signal<Record<number, OrganizationMemberResponseDto | null>>({});
  readonly teamMyRoles = signal<Record<number, 'Team Manager' | 'Member' | '—'>>({});
  readonly teamGrants = signal<Record<number, number>>({});

  // Modal Signals
  readonly isCreateModalOpen = signal<boolean>(false);
  readonly isEditModalOpen = signal<boolean>(false);
  readonly teamToEdit = signal<GroupResponseDto | null>(null);
  readonly isDeleteModalOpen = signal<boolean>(false);
  readonly teamToDelete = signal<GroupResponseDto | null>(null);
  readonly deleteModalMode = signal<DeleteModalMode>('archive');

  // Drawer Signal
  readonly selectedTeam = signal<GroupResponseDto | null>(null);
  readonly isDetailsDrawerOpen = signal<boolean>(false);

  // Context Signals & Computed Values
  readonly activeWorkspace = this.workspaceContextStore.activeWorkspace;
  readonly currentUser = this.authStore.currentUser;

  readonly orgCode = computed<string>(() => {
    // 1. From workspace context store activeOrgCode (reactive signal)
    const storeCode =
      typeof this.workspaceContextStore.activeOrgCode === 'function'
        ? this.workspaceContextStore.activeOrgCode()
        : null;
    if (storeCode) return storeCode;

    // 2. From workspace context store activeOrganization (reactive signal)
    const org =
      typeof this.workspaceContextStore.activeOrganization === 'function'
        ? this.workspaceContextStore.activeOrganization()
        : null;
    if (org?.orgCode) return org.orgCode;

    // 3. Traverse activated route snapshot hierarchy for orgCode parameter
    let current: ActivatedRoute | null = this.route;
    while (current) {
      const code = current.snapshot.paramMap?.get('orgCode');
      if (code) return code;
      current = current.parent;
    }

    // 4. Fallback to activeWorkspace organizationCode
    const ws = this.activeWorkspace();
    return (ws?.organizationCode || ws?.orgCode || '')?.toString();
  });

  /**
   * Team creation and management capability.
   * Checks direct capability, user role, and active organization workspace.
   */
  readonly canCreateTeams = computed(() => {
    const user = this.currentUser();
    const org = this.orgCode();
    if (user && org && hasOrganizationCapability(user, org, 'ORG_TEAMS_MANAGE')) {
      return true;
    }
    const role = (user?.role || '').toUpperCase();
    if (role === 'ROLE_SUPER_ADMIN' || role === 'ROLE_ORG_ADMIN' || role === 'ROLE_ORG_MANAGER') {
      return true;
    }
    const ws = this.activeWorkspace();
    return !!(ws && ws.type === 'ORGANIZATION');
  });

  // Filtered Teams Computation
  readonly filteredTeams = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const status = this.selectedStatus();
    const teams = this.allTeams();

    return teams.filter((t) => {
      // Status matching
      const teamStatus = (t.status || 'ACTIVE').toUpperCase();
      if (status !== 'ALL' && teamStatus !== status) {
        return false;
      }
      // Search query matching (name or description)
      if (query.length > 0) {
        const nameMatch = t.name.toLowerCase().includes(query);
        const descMatch = t.description
          ? t.description.toLowerCase().includes(query)
          : false;
        return nameMatch || descMatch;
      }
      return true;
    });
  });

  // Status Counts
  readonly totalCount = computed(() => this.allTeams().length);
  readonly activeCount = computed(
    () =>
      this.allTeams().filter(
        (t) => (t.status || 'ACTIVE').toUpperCase() === 'ACTIVE'
      ).length
  );
  readonly archivedCount = computed(
    () =>
      this.allTeams().filter(
        (t) => (t.status || '').toUpperCase() === 'ARCHIVED'
      ).length
  );

  readonly isFiltered = computed(
    () =>
      this.searchQuery().trim().length > 0 || this.selectedStatus() !== 'ALL'
  );

  private teamsSubscription: Subscription | null = null;
  private previousOrgCode: string | null = null;

  constructor() {
    effect(() => {
      const code = this.orgCode();
      if (code && code !== this.previousOrgCode) {
        this.previousOrgCode = code;
        this.searchQuery.set('');
        this.selectedStatus.set('ALL');
        this.loadTeams();
      }
    });
  }

  ngOnInit(): void {
    if (this.orgCode() && this.allTeams().length === 0) {
      this.loadTeams();
    }
  }

  ngOnDestroy(): void {
    if (this.teamsSubscription) {
      this.teamsSubscription.unsubscribe();
    }
  }

  loadTeams(): void {
    const code = this.orgCode();
    if (!code) {
      this.allTeams.set([]);
      this.isLoading.set(false);
      return;
    }

    this.isLoading.set(true);
    this.error.set(null);

    if (this.teamsSubscription) {
      this.teamsSubscription.unsubscribe();
    }

    this.teamsSubscription = this.organizationApiService
      .getOrganizationGroups(code)
      .subscribe({
        next: (groups) => {
          this.allTeams.set(groups || []);
          this.isLoading.set(false);
          this.enrichTeams(code, groups || []);
        },
        error: (err) => {
          const message =
            err?.error?.message ||
            err?.message ||
            'Unable to fetch organization teams. Please check network connection.';
          this.error.set(message);
          this.isLoading.set(false);
        },
      });
  }

  private enrichTeams(orgCode: string, teams: GroupResponseDto[]): void {
    for (const team of teams) {
      // 1. Fetch Boundary
      this.teamDelegationService
        .getBoundary(orgCode, team.id)
        .pipe(catchError(() => of(null)))
        .subscribe((boundary) => {
          this.teamBoundaries.update((map) => ({ ...map, [team.id]: boundary }));
        });

      // 2. Fetch Team Manager & Current User Role in Team
      this.organizationApiService
        .getGroupMembers(orgCode, team.id)
        .pipe(catchError(() => of([])))
        .subscribe((members) => {
          const manager = members.find((m) => m.groupRole === 'MANAGER') || null;
          this.teamManagers.update((map) => ({ ...map, [team.id]: manager }));

          const currentUserId = this.currentUser()?.id;
          const myMember = currentUserId ? members.find((m) => m.userId === currentUserId) : null;
          let role: 'Team Manager' | 'Member' | '—' = '—';
          if (myMember) {
            role = myMember.groupRole === 'MANAGER' ? 'Team Manager' : 'Member';
          }
          this.teamMyRoles.update((map) => ({ ...map, [team.id]: role }));
        });

      // 3. Fetch Active Delegations Count
      this.teamDelegationService
        .getMemberAccessList(orgCode, team.id)
        .pipe(catchError(() => of([])))
        .subscribe((grants) => {
          this.teamGrants.update((map) => ({ ...map, [team.id]: grants?.length || 0 }));
        });
    }
  }

  onSearchChange(query: string): void {
    this.searchQuery.set(query);
  }

  onStatusChange(status: TeamStatusFilter): void {
    this.selectedStatus.set(status);
  }

  onClearFilters(): void {
    this.searchQuery.set('');
    this.selectedStatus.set('ALL');
  }

  onCreateTeamClicked(): void {
    if (!this.canCreateTeams()) {
      this.notificationService.warning(
        'Team creation is available to organization managers and administrators.'
      );
      return;
    }
    this.isCreateModalOpen.set(true);
  }

  onTeamCreated(createdTeam: GroupResponseDto): void {
    this.isCreateModalOpen.set(false);
    this.loadTeams();
    this.notificationService.success(
      `Team "${createdTeam.name}" created successfully.`
    );
  }

  onTeamCardClicked(team: GroupResponseDto): void {
    this.selectedTeam.set(team);
    this.isDetailsDrawerOpen.set(true);
  }

  onDetailsDrawerClosed(): void {
    this.isDetailsDrawerOpen.set(false);
    this.selectedTeam.set(null);
  }

  // ── Edit Team Handlers ────────────────────────────────
  onEditTeam(team: GroupResponseDto): void {
    this.teamToEdit.set(team);
    this.isEditModalOpen.set(true);
  }

  onEditModalClosed(): void {
    this.isEditModalOpen.set(false);
    this.teamToEdit.set(null);
  }

  onTeamUpdated(updatedTeam: GroupResponseDto): void {
    this.isEditModalOpen.set(false);
    this.teamToEdit.set(null);
    this.loadTeams();
    this.notificationService.success(
      `Team "${updatedTeam.name}" updated successfully.`
    );
  }

  // ── Delete / Archive Handlers ─────────────────────────
  onArchiveTeam(team: GroupResponseDto): void {
    this.teamToDelete.set(team);
    this.deleteModalMode.set('archive');
    this.isDeleteModalOpen.set(true);
  }

  onUnarchiveTeam(team: GroupResponseDto): void {
    this.teamToDelete.set(team);
    this.deleteModalMode.set('unarchive');
    this.isDeleteModalOpen.set(true);
  }

  onDeleteTeam(team: GroupResponseDto): void {
    this.teamToDelete.set(team);
    this.deleteModalMode.set('delete');
    this.isDeleteModalOpen.set(true);
  }

  onDeleteModalClosed(): void {
    this.isDeleteModalOpen.set(false);
    this.teamToDelete.set(null);
  }

  onTeamActionCompleted(event: { action: DeleteModalMode; team: GroupResponseDto }): void {
    this.isDeleteModalOpen.set(false);
    this.teamToDelete.set(null);
    this.loadTeams();

    const name = event.team.name;
    if (event.action === 'delete') {
      this.notificationService.success(`Team "${name}" was permanently deleted.`);
    } else if (event.action === 'archive') {
      this.notificationService.success(`Team "${name}" has been archived.`);
    } else {
      this.notificationService.success(`Team "${name}" was reactivated.`);
    }
  }
}

