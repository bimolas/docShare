import { Injectable, signal, computed, inject } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map, tap, switchMap } from 'rxjs/operators';
import { OrganizationApiService } from '../../core/services/organization-api.service';
import { WorkspaceApiService } from '../../core/services/workspace-api.service';
import { SubspacePersistenceService } from '../../core/services/subspace-persistence.service';
import { AuthStore } from '../auth/auth.store';
import { OrganizationModel, OrganizationResponseDto } from '../../core/models/organization.model';
import {
  ProjectWorkspaceModel,
  PersonalWorkspaceModel,
  ActiveWorkspaceModel,
} from '../../core/models/project-workspace.model';
import {
  WorkspaceInfo,
  SubspaceInfo,
} from '../../core/models/workspace-context.model';

// @deprecated Temporary compatibility helper: allow unmigrated callers to invoke .subscribe() on boolean return values
declare global {
  interface Boolean {
    subscribe(observer?: any): { unsubscribe(): void };
  }
}

if (typeof Boolean.prototype !== 'undefined' && !(Boolean.prototype as any).subscribe) {
  (Boolean.prototype as any).subscribe = function (observer?: any) {
    const val = this.valueOf();
    if (typeof observer === 'function') observer(val);
    else if (observer && observer.next) observer.next(val);
    return { unsubscribe() {} };
  };
}

@Injectable({ providedIn: 'root' })
export class WorkspaceContextStore {
  private readonly orgApiService = inject(OrganizationApiService);
  private readonly workspaceApiService = inject(WorkspaceApiService);
  private readonly subspacePersistenceService = inject(SubspacePersistenceService);
  private readonly authStore = inject(AuthStore);

  // ── Canonical State Signals ────────────────────────────────────────────────
  private readonly _activeOrganization = signal<OrganizationModel | null>(null);
  private readonly _activeWorkspace = signal<ProjectWorkspaceModel | null>(null);
  private readonly _availableOrganizations = signal<OrganizationModel[]>([]);
  private readonly _availableWorkspaces = signal<ProjectWorkspaceModel[]>([]);
  private readonly _isLoading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);

  private _lastOrgsLoadedAt = 0;
  private readonly TTL_MS = 5 * 60 * 1000; // 5 minutes TTL

  // ── Canonical Readonly Signals ─────────────────────────────────────────────
  readonly activeOrganization = this._activeOrganization.asReadonly();
  readonly activeWorkspace = this._activeWorkspace.asReadonly();
  readonly availableOrganizations = this._availableOrganizations.asReadonly();
  readonly availableWorkspaces = this._availableWorkspaces.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();
  readonly error = this._error.asReadonly();

  // ── Canonical Computed State ───────────────────────────────────────────────
  readonly isPersonal = computed(() => !this._activeOrganization());
  readonly isOrganization = computed(() => !!this._activeOrganization());
  readonly activeOrgCode = computed(() => this._activeOrganization()?.orgCode ?? null);
  readonly activeRootFolderId = computed(() => this._activeWorkspace()?.rootFolderId ?? null);

  // ── @deprecated Temporary Compatibility Signals for Unmigrated UI Components ─
  /** @deprecated Use isPersonal instead. */
  readonly isPersonalWorkspace = computed(() => !this._activeOrganization());
  /** @deprecated Use isOrganization instead. */
  readonly isOrganizationWorkspace = computed(() => !!this._activeOrganization());

  /** @deprecated Temporary compatibility signal for unmigrated UI components. */
  readonly activeSubspace = computed<SubspaceInfo | null>(() => {
    const ws = this._activeWorkspace();
    const org = this._activeOrganization();
    if (!ws) return null;
    return {
      id: ws.id.toString(),
      folderId: ws.rootFolderId,
      name: ws.name,
      workspaceId: org?.orgCode ?? 'personal',
      isDefault: ws.isDefault ?? false,
      createdAt: ws.createdAt,
    };
  });

  /** @deprecated Temporary compatibility signal for unmigrated UI components. */
  readonly availableSubspaces = computed<SubspaceInfo[]>(() => {
    const org = this._activeOrganization();
    return this._availableWorkspaces().map((ws) => ({
      id: ws.id.toString(),
      folderId: ws.rootFolderId,
      name: ws.name,
      workspaceId: org?.orgCode ?? 'personal',
      isDefault: ws.isDefault ?? false,
      createdAt: ws.createdAt,
    }));
  });

  /** @deprecated Temporary compatibility signal for unmigrated UI components. */
  readonly activeWorkspaceId = computed<string>(() => {
    const ws = this._activeWorkspace();
    if (!ws) return 'personal';
    return ws.id.toString();
  });

  /** @deprecated Temporary compatibility signal for unmigrated UI components. */
  readonly activeSubspaceId = computed<string>(() => {
    const ws = this._activeWorkspace();
    if (!ws) return 'default';
    return ws.id.toString();
  });

  // ── State Reset ────────────────────────────────────────────────────────────
  /**
   * Resets all organization and workspace context state on logout or tenant switch.
   */
  reset(): void {
    this._activeOrganization.set(null);
    this._activeWorkspace.set(null);
    this._availableOrganizations.set([]);
    this._availableWorkspaces.set([]);
    this._isLoading.set(false);
    this._error.set(null);
    this._lastOrgsLoadedAt = 0;
  }

  // ── Canonical Organization Loading & Selection ─────────────────────────────
  /**
   * Loads accessible user organizations.
   * Centralized frontend source of truth for organizations.
   */
  loadUserOrganizations(force = false): Observable<OrganizationModel[]> {
    if (
      !force &&
      this._lastOrgsLoadedAt > 0 &&
      Date.now() - this._lastOrgsLoadedAt < this.TTL_MS &&
      this._availableOrganizations().length > 0
    ) {
      return of(this._availableOrganizations());
    }

    this._isLoading.set(true);
    this._error.set(null);

    return this.orgApiService.getUserOrganizations().pipe(
      map((orgDtos: OrganizationResponseDto[]) => {
        const orgModels: OrganizationModel[] = (orgDtos || []).map((org) => ({
          orgCode: org.orgCode,
          name: org.name,
        }));
        this._availableOrganizations.set(orgModels);
        this._lastOrgsLoadedAt = Date.now();
        this._isLoading.set(false);
        return orgModels;
      }),
      catchError((err) => {
        console.error('Failed to load user organizations:', err);
        this._availableOrganizations.set([]);
        this._isLoading.set(false);
        this._error.set('Failed to load organizations');
        return of([]);
      })
    );
  }

  /**
   * Selects active organization by orgCode and loads its canonical project workspaces.
   * Deduplicates requests: if already active and cached, returns existing workspaces.
   */
  selectOrganization(orgCode: string, force = false): Observable<ProjectWorkspaceModel[]> {
    const currentOrg = this._activeOrganization();
    const currentWorkspaces = this._availableWorkspaces();

    if (
      !force &&
      currentOrg?.orgCode === orgCode &&
      currentWorkspaces.length > 0
    ) {
      this.ensureActiveOrgWorkspaceSelected(orgCode, currentWorkspaces);
      return of(currentWorkspaces);
    }

    this._isLoading.set(true);
    this._error.set(null);

    return this.loadUserOrganizations().pipe(
      switchMap((orgs) => {
        const matchedOrg = orgs.find((o) => o.orgCode === orgCode);
        if (!matchedOrg) {
          const errMsg = `Organization "${orgCode}" not found or unauthorized`;
          this._error.set(errMsg);
          this._isLoading.set(false);
          return throwError(() => new Error(errMsg));
        }

        this._activeOrganization.set(matchedOrg);

        // Fetch canonical project workspaces for organization
        return this.workspaceApiService.getWorkspacesForOrganization(orgCode).pipe(
          tap((workspaces) => {
            const wsList = workspaces || [];
            this._availableWorkspaces.set(wsList);
            this.ensureActiveOrgWorkspaceSelected(orgCode, wsList);
            this._isLoading.set(false);
          }),
          catchError((err) => {
            console.error(`Failed to load workspaces for org "${orgCode}":`, err);
            this._availableWorkspaces.set([]);
            this._activeWorkspace.set(null);
            this._isLoading.set(false);
            this._error.set(`Failed to load workspaces for organization ${orgCode}`);
            return of([]);
          })
        );
      })
    );
  }

  // ── Canonical Personal Workspace Loading ────────────────────────────────────
  /**
   * Loads the authenticated user's personal workspaces.
   * Sets activeOrganization to null and isolates availableWorkspaces.
   */
  loadPersonalWorkspaces(force = false): Observable<ProjectWorkspaceModel[]> {
    if (
      !force &&
      this.isPersonal() &&
      this._availableWorkspaces().length > 0
    ) {
      this.ensureActivePersonalWorkspaceSelected(this._availableWorkspaces());
      return of(this._availableWorkspaces());
    }

    this._isLoading.set(true);
    this._error.set(null);
    this._activeOrganization.set(null);

    return this.workspaceApiService.getPersonalWorkspaces().pipe(
      tap((workspaces) => {
        const wsList = workspaces || [];
        this._availableWorkspaces.set(wsList);
        this.ensureActivePersonalWorkspaceSelected(wsList);
        this._isLoading.set(false);
      }),
      catchError((err) => {
        console.error('Failed to load personal workspaces:', err);
        this._availableWorkspaces.set([]);
        this._activeWorkspace.set(null);
        this._isLoading.set(false);
        this._error.set('Failed to load personal workspaces');
        return of([]);
      })
    );
  }

  // ── Canonical Workspace Selection ──────────────────────────────────────────
  /**
   * Selects active workspace by numeric workspaceId.
   * Verifies the workspace belongs to the active context (org or personal) and is ACTIVE.
   * Returns true on success, false if invalid/inaccessible.
   */
  selectWorkspace(workspaceId: number): boolean {
    if (typeof workspaceId !== 'number' || isNaN(workspaceId) || workspaceId <= 0) {
      this._error.set(`Invalid workspace ID: ${workspaceId}`);
      return false;
    }

    const currentOrg = this._activeOrganization();
    const isPersonalContext = !currentOrg;
    const userId = this.authStore.currentUser()?.id;

    const matchedWs = this._availableWorkspaces().find((w) => w.id === workspaceId);
    if (!matchedWs) {
      const scopeName = isPersonalContext ? 'Personal Space' : `organization ${currentOrg!.orgCode}`;
      this._error.set(`Workspace #${workspaceId} not found in ${scopeName}`);
      return false;
    }

    if (isPersonalContext) {
      if (matchedWs.organizationCode !== null) {
        this._error.set(`Workspace #${workspaceId} is not a personal workspace`);
        return false;
      }
    } else {
      if (matchedWs.organizationCode !== currentOrg!.orgCode) {
        this._error.set(`Workspace #${workspaceId} does not belong to organization ${currentOrg!.orgCode}`);
        return false;
      }
    }

    if (matchedWs.status !== 'ACTIVE') {
      this._error.set(`Workspace #${workspaceId} is not active (status: ${matchedWs.status})`);
      return false;
    }

    this._activeWorkspace.set(matchedWs);

    // Persist active workspace preference (null orgCode for personal)
    this.subspacePersistenceService.saveWorkspacePreference(
      userId,
      isPersonalContext ? null : currentOrg!.orgCode,
      workspaceId
    );

    return true;
  }

  /**
   * Resolves the default numeric workspace ID for an organization.
   * Follows strict resolution priority:
   * 1. Persisted workspace preference
   * 2. Backend default workspace marker (isDefault === true)
   * 3. First ACTIVE workspace
   */
  resolveDefaultWorkspace(orgCode: string): Observable<number> {
    return this.selectOrganization(orgCode).pipe(
      map((workspaces) => {
        const activeWorkspaces = (workspaces || []).filter((w) => w.status === 'ACTIVE');
        if (activeWorkspaces.length === 0) {
          throw new Error(`Organization "${orgCode}" has no active workspaces`);
        }

        const userId = this.authStore.currentUser()?.id;

        // 1. Check persisted preference
        const persistedId = this.subspacePersistenceService.getWorkspacePreference(
          userId,
          orgCode,
          activeWorkspaces
        );
        if (persistedId) {
          return persistedId;
        }

        // 2. Check backend default marker
        const defaultWs = activeWorkspaces.find((w) => w.isDefault === true);
        if (defaultWs) {
          return defaultWs.id;
        }

        // 3. Fallback to first active workspace
        return activeWorkspaces[0].id;
      })
    );
  }

  /**
   * Resolves the default numeric workspace ID for Personal Space.
   * Follows strict resolution priority:
   * 1. Persisted personal preference
   * 2. Backend default workspace marker (isDefault === true)
   * 3. First ACTIVE personal workspace
   * If user has ZERO personal workspaces, returns null (non-fatal).
   */
  resolveDefaultPersonalWorkspace(): Observable<number | null> {
    return this.loadPersonalWorkspaces().pipe(
      map((workspaces) => {
        const activeWorkspaces = (workspaces || []).filter((w) => w.status === 'ACTIVE');
        if (activeWorkspaces.length === 0) {
          return null;
        }

        const userId = this.authStore.currentUser()?.id;

        // 1. Check persisted personal preference
        const persistedId = this.subspacePersistenceService.getWorkspacePreference(
          userId,
          null,
          activeWorkspaces
        );
        if (persistedId) {
          return persistedId;
        }

        // 2. Check backend default marker
        const defaultWs = activeWorkspaces.find((w) => w.isDefault === true);
        if (defaultWs) {
          return defaultWs.id;
        }

        // 3. Fallback to first active workspace
        return activeWorkspaces[0].id;
      })
    );
  }

  // ── Personal Workspace Selection ───────────────────────────────────────────
  /**
   * Selects Personal Workspace context and resolves the active personal workspace.
   * If workspaceId is provided, activates that workspace.
   * If workspaceId is not provided, resolves default.
   * If zero personal workspaces exist, activeWorkspace becomes null without error.
   */
  selectPersonalWorkspace(workspaceId?: number): Observable<ProjectWorkspaceModel | null> {
    this._activeOrganization.set(null);
    this._error.set(null);

    if (typeof workspaceId === 'number' && workspaceId > 0) {
      return this.loadPersonalWorkspaces().pipe(
        map(() => {
          this.selectWorkspace(workspaceId);
          return this._activeWorkspace();
        })
      );
    }

    return this.resolveDefaultPersonalWorkspace().pipe(
      map((defaultId) => {
        if (defaultId !== null) {
          this.selectWorkspace(defaultId);
        } else {
          this._activeWorkspace.set(null);
        }
        return this._activeWorkspace();
      }),
      catchError((err) => {
        console.error('Failed to resolve personal workspace:', err);
        this._activeWorkspace.set(null);
        return of(null);
      })
    );
  }

  /**
   * Directly sets the active workspace model.
   */
  setActiveWorkspace(ws: ProjectWorkspaceModel | null): void {
    this._activeWorkspace.set(ws);
    if (ws) {
      if (!this._availableWorkspaces().some((w) => w.id === ws.id)) {
        this._availableWorkspaces.set([...this._availableWorkspaces(), ws]);
      }
      const userId = this.authStore.currentUser()?.id;
      this.subspacePersistenceService.saveWorkspacePreference(
        userId,
        ws.organizationCode,
        ws.id
      );
    }
  }

  // ── Workspace Context Bootstrap Helpers ─────────────────────────────────────
  private ensureActivePersonalWorkspaceSelected(workspaces: ProjectWorkspaceModel[]): void {
    const activeWorkspaces = (workspaces || []).filter(
      (w) => w.status === 'ACTIVE' && (w.organizationCode === null || w.organizationCode === undefined)
    );

    if (activeWorkspaces.length === 0) {
      this._activeWorkspace.set(null);
      return;
    }

    const currentActive = this._activeWorkspace();
    // Retain currently active workspace if it is already personal and still active in the list
    if (
      currentActive &&
      (currentActive.organizationCode === null || currentActive.organizationCode === undefined) &&
      activeWorkspaces.some((w) => w.id === currentActive.id)
    ) {
      return;
    }

    const userId = this.authStore.currentUser()?.id;

    // 1. Check persisted preference (getWorkspacePreference purges stale keys automatically)
    const persistedId = this.subspacePersistenceService.getWorkspacePreference(userId, null, activeWorkspaces);
    if (persistedId) {
      const matched = activeWorkspaces.find((w) => w.id === persistedId);
      if (matched) {
        this._activeWorkspace.set(matched);
        return;
      }
    }

    // 2. Check backend default workspace marker (isDefault === true)
    const defaultWs = activeWorkspaces.find((w) => w.isDefault === true);
    if (defaultWs) {
      this._activeWorkspace.set(defaultWs);
      this.subspacePersistenceService.saveWorkspacePreference(userId, null, defaultWs.id);
      return;
    }

    // 3. Fallback to first active workspace
    const firstWs = activeWorkspaces[0];
    this._activeWorkspace.set(firstWs);
    this.subspacePersistenceService.saveWorkspacePreference(userId, null, firstWs.id);
  }

  private ensureActiveOrgWorkspaceSelected(orgCode: string, workspaces: ProjectWorkspaceModel[]): void {
    const activeWorkspaces = (workspaces || []).filter(
      (w) => w.status === 'ACTIVE' && w.organizationCode === orgCode
    );

    if (activeWorkspaces.length === 0) {
      this._activeWorkspace.set(null);
      return;
    }

    const currentActive = this._activeWorkspace();
    // Retain currently active workspace if it belongs to this organization and is still active in the list
    if (
      currentActive &&
      currentActive.organizationCode === orgCode &&
      activeWorkspaces.some((w) => w.id === currentActive.id)
    ) {
      return;
    }

    const userId = this.authStore.currentUser()?.id;

    // 1. Check persisted preference (getWorkspacePreference purges stale keys automatically)
    const persistedId = this.subspacePersistenceService.getWorkspacePreference(userId, orgCode, activeWorkspaces);
    if (persistedId) {
      const matched = activeWorkspaces.find((w) => w.id === persistedId);
      if (matched) {
        this._activeWorkspace.set(matched);
        return;
      }
    }

    // 2. Check backend default workspace marker (isDefault === true)
    const defaultWs = activeWorkspaces.find((w) => w.isDefault === true);
    if (defaultWs) {
      this._activeWorkspace.set(defaultWs);
      this.subspacePersistenceService.saveWorkspacePreference(userId, orgCode, defaultWs.id);
      return;
    }

    // 3. Fallback to first active workspace
    const firstWs = activeWorkspaces[0];
    this._activeWorkspace.set(firstWs);
    this.subspacePersistenceService.saveWorkspacePreference(userId, orgCode, firstWs.id);
  }

  // ── @deprecated Temporary Compatibility Methods for Unmigrated UI Components ─
  /**
   * @deprecated Temporary method for unmigrated sidebar/legacy components.
   * Use loadUserOrganizations() and selectOrganization() instead.
   */
  loadUserWorkspaces(force = false): Observable<WorkspaceInfo[]> {
    const personalWs: WorkspaceInfo = {
      id: 'personal',
      name: 'Personal Workspace',
      type: 'PERSONAL',
    };

    return this.loadUserOrganizations(force).pipe(
      map((orgs) => {
        const orgWorkspaces: WorkspaceInfo[] = orgs.map((org) => ({
          id: org.orgCode,
          name: org.name,
          type: 'ORGANIZATION',
          orgCode: org.orgCode,
        }));
        return [personalWs, ...orgWorkspaces];
      })
    );
  }

  /**
   * @deprecated Temporary method for unmigrated UI components.
   */
  selectSubspace(subspaceId: string): void {
    // No-op in canonical model. State is managed by selectWorkspace.
  }
}
