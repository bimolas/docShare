import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError, Subject } from 'rxjs';
import { signal } from '@angular/core';
import { DashboardStore } from './dashboard.store';
import { DashboardApiService } from '../services/dashboard-api.service';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import {
  PersonalDashboardSummary,
  OrganizationDashboardSummary,
} from '../models/dashboard.model';

describe('DashboardStore', () => {
  let store: DashboardStore;
  let mockApi: jasmine.SpyObj<DashboardApiService>;
  let mockWorkspaceContext: {
    isPersonal: ReturnType<typeof signal<boolean>>;
    isOrganization: ReturnType<typeof signal<boolean>>;
    activeOrgCode: ReturnType<typeof signal<string | null>>;
  };

  const samplePersonalSummary: PersonalDashboardSummary = {
    usedStorageBytes: 524288000, // 500 MB
    storageLimitBytes: 10737418240, // 10 GB
    storageUsagePercentage: 4.88,
    usedStorageFormatted: '500.0 MB',
    documentCount: 20,
    folderCount: 5,
    pendingAccessReviewsCount: 3,
    myPendingAccessRequestsCount: 1,
    activeDirectSharesCount: 7,
    recentActivityTrend: [
      { date: '2026-09-08', count: 4, intensity: 2 },
      { date: '2026-09-09', count: 9, intensity: 3 },
    ],
  };

  const sampleOrgSummary: OrganizationDashboardSummary = {
    orgCode: 'CORP1',
    orgName: 'Corp Global',
    usedStorageBytes: 10737418240, // 10 GB
    storageLimitBytes: 53687091200, // 50 GB
    storageUsagePercentage: 20.0,
    usedStorageFormatted: '10.0 GB',
    documentCount: 400,
    folderCount: 50,
    workspaceCount: 8,
    teamCount: 6,
    memberCount: 45,
    pendingAccessRequestsCount: 6,
    pendingInvitationsCount: 3,
    recentActivityTrend: [
      { date: '2026-09-08', count: 18, intensity: 4 },
      { date: '2026-09-09', count: 24, intensity: 4 },
    ],
  };

  beforeEach(() => {
    mockApi = jasmine.createSpyObj<DashboardApiService>('DashboardApiService', [
      'getPersonalSummary',
      'getOrganizationSummary',
    ]);
    mockApi.getPersonalSummary.and.returnValue(of(samplePersonalSummary));
    mockApi.getOrganizationSummary.and.returnValue(of(sampleOrgSummary));

    mockWorkspaceContext = {
      isPersonal: signal<boolean>(true),
      isOrganization: signal<boolean>(false),
      activeOrgCode: signal<string | null>(null),
    };

    TestBed.configureTestingModule({
      providers: [
        DashboardStore,
        { provide: DashboardApiService, useValue: mockApi },
        { provide: WorkspaceContextStore, useValue: mockWorkspaceContext },
      ],
    });

    store = TestBed.inject(DashboardStore);
    // Disable auto-sync during manual step-by-step state verification to isolate testing
    store.autoSyncEnabled.set(false);
  });

  describe('Initial State', () => {
    it('should initialize with PERSONAL scope and null summaries', () => {
      expect(store.scope()).toBe('PERSONAL');
      expect(store.isPersonal()).toBeTrue();
      expect(store.isOrganization()).toBeFalse();
      expect(store.personalSummary()).toBeNull();
      expect(store.organizationSummary()).toBeNull();
      expect(store.isLoading()).toBeFalse();
      expect(store.isRefreshing()).toBeFalse();
      expect(store.error()).toBeNull();
      expect(store.hasData()).toBeFalse();
    });
  });

  describe('Personal Dashboard Loading & Isolation', () => {
    it('should load personal summary and set signals correctly', () => {
      store.loadPersonalSummary().subscribe((result) => {
        expect(result).toEqual(samplePersonalSummary);
      });

      expect(mockApi.getPersonalSummary).toHaveBeenCalledTimes(1);
      expect(store.personalSummary()).toEqual(samplePersonalSummary);
      expect(store.activeSummary()).toEqual(samplePersonalSummary);
      expect(store.isLoading()).toBeFalse();
      expect(store.error()).toBeNull();
      expect(store.hasData()).toBeTrue();
      expect(store.lastLoadedAt()).toBeGreaterThan(0);
    });

    it('should NOT overwrite organizationSummary when personalSummary is loaded', () => {
      // Pre-set org summary
      store.organizationSummary.set(sampleOrgSummary);

      store.loadPersonalSummary().subscribe();

      expect(store.personalSummary()).toEqual(samplePersonalSummary);
      expect(store.organizationSummary()).toEqual(sampleOrgSummary);
    });
  });

  describe('Organization Dashboard Loading & Isolation', () => {
    beforeEach(() => {
      store.scope.set('ORGANIZATION');
    });

    it('should load organization summary for specified orgCode', () => {
      store.loadOrganizationSummary('CORP1').subscribe((result) => {
        expect(result).toEqual(sampleOrgSummary);
      });

      expect(mockApi.getOrganizationSummary).toHaveBeenCalledWith('CORP1');
      expect(store.organizationSummary()).toEqual(sampleOrgSummary);
      expect(store.activeSummary()).toEqual(sampleOrgSummary);
      expect(store.activeOrgCode()).toBe('CORP1');
      expect(store.isLoading()).toBeFalse();
      expect(store.error()).toBeNull();
    });

    it('should NOT overwrite personalSummary when organizationSummary is loaded', () => {
      // Pre-set personal summary
      store.personalSummary.set(samplePersonalSummary);

      store.loadOrganizationSummary('CORP1').subscribe();

      expect(store.personalSummary()).toEqual(samplePersonalSummary);
      expect(store.organizationSummary()).toEqual(sampleOrgSummary);
    });

    it('should reject empty orgCode with error signal', () => {
      store.loadOrganizationSummary('   ').subscribe((result) => {
        expect(result).toBeNull();
      });

      expect(store.error()).toBe('Organization code is required');
      expect(mockApi.getOrganizationSummary).not.toHaveBeenCalled();
    });
  });

  describe('Context Switching & Active Summary', () => {
    beforeEach(() => {
      store.personalSummary.set(samplePersonalSummary);
      store.organizationSummary.set(sampleOrgSummary);
    });

    it('should return personalSummary when scope is PERSONAL', () => {
      store.scope.set('PERSONAL');
      expect(store.isPersonal()).toBeTrue();
      expect(store.isOrganization()).toBeFalse();
      expect(store.activeSummary()).toEqual(samplePersonalSummary);
    });

    it('should return organizationSummary when scope is ORGANIZATION', () => {
      store.scope.set('ORGANIZATION');
      expect(store.isPersonal()).toBeFalse();
      expect(store.isOrganization()).toBeTrue();
      expect(store.activeSummary()).toEqual(sampleOrgSummary);
    });

    it('should compute appropriate chart view models when switching contexts', () => {
      // 1. Personal context
      store.scope.set('PERSONAL');
      const personalGauge = store.storageGaugeViewModel();
      expect(personalGauge).not.toBeNull();
      expect(personalGauge!.percentage).toBe(4.88);
      expect(personalGauge!.formattedUsed).toBe('500.0 MB');

      // 2. Switch to Organization context
      store.scope.set('ORGANIZATION');
      const orgGauge = store.storageGaugeViewModel();
      expect(orgGauge).not.toBeNull();
      expect(orgGauge!.percentage).toBe(20.0);
      expect(orgGauge!.formattedUsed).toBe('10.0 GB');
    });
  });

  describe('Auto-Sync Reactivity with WorkspaceContextStore', () => {
    it('should automatically switch scope and trigger loads on context changes', () => {
      store.autoSyncEnabled.set(true);

      // 1. Initially personal
      mockWorkspaceContext.isPersonal.set(true);
      mockWorkspaceContext.activeOrgCode.set(null);
      TestBed.flushEffects();

      expect(store.scope()).toBe('PERSONAL');
      expect(mockApi.getPersonalSummary).toHaveBeenCalled();

      // 2. Context changes to organization CORP1
      mockWorkspaceContext.isPersonal.set(false);
      mockWorkspaceContext.activeOrgCode.set('CORP1');
      TestBed.flushEffects();

      expect(store.scope()).toBe('ORGANIZATION');
      expect(mockApi.getOrganizationSummary).toHaveBeenCalledWith('CORP1');
    });
  });


  describe('Loading and Error Handling', () => {
    it('should set error signal and clear loading on personal API failure', () => {
      mockApi.getPersonalSummary.and.returnValue(
        throwError(() => ({ message: 'Network offline' }))
      );

      store.loadPersonalSummary().subscribe();

      expect(store.error()).toBe('Network offline');
      expect(store.isLoading()).toBeFalse();
      expect(store.isRefreshing()).toBeFalse();
      expect(store.personalSummary()).toBeNull();
    });

    it('should set isRefreshing on forceRefresh', () => {
      // Mock delayed return to verify refreshing flag during flight
      store.personalSummary.set(samplePersonalSummary);

      store.loadPersonalSummary(true).subscribe();

      // After completion, isRefreshing should reset
      expect(store.isRefreshing()).toBeFalse();
      expect(store.personalSummary()).toEqual(samplePersonalSummary);
    });
  });

  describe('Empty State Computation', () => {
    it('should recognize completely empty personal space', () => {
      store.scope.set('PERSONAL');
      store.personalSummary.set({
        ...samplePersonalSummary,
        documentCount: 0,
        folderCount: 0,
        usedStorageBytes: 0,
      });

      expect(store.isEmpty()).toBeTrue();
    });

    it('should recognize non-empty personal space', () => {
      store.scope.set('PERSONAL');
      store.personalSummary.set(samplePersonalSummary);

      expect(store.isEmpty()).toBeFalse();
    });

    it('should recognize completely empty organization space', () => {
      store.scope.set('ORGANIZATION');
      store.organizationSummary.set({
        ...sampleOrgSummary,
        documentCount: 0,
        folderCount: 0,
        workspaceCount: 0,
      });

      expect(store.isEmpty()).toBeTrue();
    });
  });

  describe('Activity Trend View Model Calculation', () => {
    it('should compute totals, dates, counts and peak action count correctly', () => {
      store.scope.set('PERSONAL');
      store.personalSummary.set(samplePersonalSummary);

      const vm = store.activityTrendViewModel();
      expect(vm).not.toBeNull();
      expect(vm!.dates).toEqual(['2026-09-08', '2026-09-09']);
      expect(vm!.counts).toEqual([4, 9]);
      expect(vm!.totalActions).toBe(13);
      expect(vm!.peakCount).toBe(9);
    });
  });

  describe('Future Workspace Scope Handling (Explicitly Unsupported)', () => {
    it('should transition to WORKSPACE scope without impacting personal/org caches and mark as unsupported', () => {
      store.personalSummary.set(samplePersonalSummary);
      store.organizationSummary.set(sampleOrgSummary);

      store.prepareWorkspaceScope(101);

      expect(store.scope()).toBe('WORKSPACE');
      expect(store.isWorkspace()).toBeTrue();
      expect(store.isUnsupportedScope()).toBeTrue();
      expect(store.isPersonal()).toBeFalse();
      expect(store.isOrganization()).toBeFalse();
      expect(store.error()).toBe('Workspace dashboard is not supported yet');

      // Crucial: activeSummary MUST be null and not fall back to organizationSummary or personalSummary
      expect(store.activeSummary()).toBeNull();
      expect(store.hasData()).toBeFalse();
      expect(store.isEmpty()).toBeFalse();

      // ViewModels and chart options must not return misleading data
      expect(store.storageGaugeViewModel()).toBeNull();
      expect(store.storageGaugeOptions()).toEqual({});
      expect(store.activityTrendViewModel()).toBeNull();
      expect(store.activityTrendOptions()).toEqual({});

      // Caches must remain intact for when user switches back
      expect(store.personalSummary()).toEqual(samplePersonalSummary);
      expect(store.organizationSummary()).toEqual(sampleOrgSummary);
    });

    it('should reject loading current context when in WORKSPACE scope', () => {
      store.prepareWorkspaceScope(202);

      store.loadCurrentContext().subscribe((result) => {
        expect(result).toBeNull();
      });

      expect(store.error()).toBe('Workspace dashboard is not supported yet');
      expect(mockApi.getPersonalSummary).not.toHaveBeenCalled();
      expect(mockApi.getOrganizationSummary).not.toHaveBeenCalled();
    });
  });

  describe('Context-Switch Request Safety & Stale Response Protection', () => {
    it('should discard delayed personal response when context switches to organization before response arrives', fakeAsync(() => {
      const delayedPersonal$ = new Subject<PersonalDashboardSummary>();
      mockApi.getPersonalSummary.and.returnValue(delayedPersonal$);

      // 1. In personal context, start loading
      store.scope.set('PERSONAL');
      store.loadPersonalSummary().subscribe();
      expect(store.isLoading()).toBeTrue();

      // 2. User navigates to organization CORP1 before personal request completes
      mockWorkspaceContext.isPersonal.set(false);
      mockWorkspaceContext.activeOrgCode.set('CORP1');
      store.scope.set('ORGANIZATION');
      store.loadOrganizationSummary('CORP1').subscribe();
      expect(store.organizationSummary()).toEqual(sampleOrgSummary);

      // 3. Delayed personal request completes late
      delayedPersonal$.next(samplePersonalSummary);
      delayedPersonal$.complete();
      tick();

      // 4. Stale personal response must NOT overwrite organization state or active summary
      expect(store.scope()).toBe('ORGANIZATION');
      expect(store.activeSummary()).toEqual(sampleOrgSummary);
      expect(store.organizationSummary()).toEqual(sampleOrgSummary);
      expect(store.isLoading()).toBeFalse();
    }));

    it('should discard delayed organization A response when context switches to organization B before response arrives', fakeAsync(() => {
      const delayedOrgA$ = new Subject<OrganizationDashboardSummary>();
      const delayedOrgB$ = new Subject<OrganizationDashboardSummary>();

      mockApi.getOrganizationSummary.and.callFake((code: string) => {
        if (code === 'ORG_A') return delayedOrgA$;
        if (code === 'ORG_B') return delayedOrgB$;
        return of(sampleOrgSummary);
      });

      store.scope.set('ORGANIZATION');
      mockWorkspaceContext.isPersonal.set(false);
      mockWorkspaceContext.activeOrgCode.set('ORG_A');

      // 1. Start loading ORG_A
      store.loadOrganizationSummary('ORG_A').subscribe();
      expect(store.isLoading()).toBeTrue();

      // 2. Switch to ORG_B while ORG_A is still in flight
      mockWorkspaceContext.activeOrgCode.set('ORG_B');
      store.loadOrganizationSummary('ORG_B').subscribe();
      expect(store.isLoading()).toBeTrue();
      // Old org summary must have been cleared during switch so Org A isn't shown
      expect(store.organizationSummary()).toBeNull();

      // 3. ORG_B completes first
      const orgBSummary = { ...sampleOrgSummary, orgCode: 'ORG_B', orgName: 'Org B Corp' };
      delayedOrgB$.next(orgBSummary);
      delayedOrgB$.complete();
      tick();

      expect(store.organizationSummary()).toEqual(orgBSummary);
      expect(store.activeSummary()).toEqual(orgBSummary);
      expect(store.isLoading()).toBeFalse();

      // 4. Now ORG_A delayed response arrives late
      const orgASummary = { ...sampleOrgSummary, orgCode: 'ORG_A', orgName: 'Org A Corp' };
      delayedOrgA$.next(orgASummary);
      delayedOrgA$.complete();
      tick();

      // 5. Must NOT overwrite ORG_B
      expect(store.organizationSummary()).toEqual(orgBSummary);
      expect(store.organizationSummary()?.orgCode).toBe('ORG_B');
      expect(store.activeSummary()).toEqual(orgBSummary);
    }));

    it('should not let an error from a superseded request overwrite active error or clear active loading state', fakeAsync(() => {
      const failingPersonal$ = new Subject<PersonalDashboardSummary>();
      const delayedOrg$ = new Subject<OrganizationDashboardSummary>();

      mockApi.getPersonalSummary.and.returnValue(failingPersonal$);
      mockApi.getOrganizationSummary.and.returnValue(delayedOrg$);

      // Start personal load
      store.scope.set('PERSONAL');
      store.loadPersonalSummary().subscribe();

      // Switch to organization
      store.scope.set('ORGANIZATION');
      mockWorkspaceContext.isPersonal.set(false);
      mockWorkspaceContext.activeOrgCode.set('CORP1');
      store.loadOrganizationSummary('CORP1').subscribe();
      expect(store.isLoading()).toBeTrue();

      // Superseded personal request fails
      failingPersonal$.error(new Error('Personal service unavailable'));
      tick();

      // Store error must not be populated with personal error, and org loading must not be cleared
      expect(store.error()).toBeNull();
      expect(store.isLoading()).toBeTrue();

      // Active org completes
      delayedOrg$.next(sampleOrgSummary);
      delayedOrg$.complete();
      tick();

      expect(store.organizationSummary()).toEqual(sampleOrgSummary);
      expect(store.isLoading()).toBeFalse();
      expect(store.error()).toBeNull();
    }));
  });

  describe('Duplicate-Load Prevention & In-Flight Deduplication', () => {
    it('should deduplicate concurrent in-flight requests for personal summary', () => {
      const delayedPersonal$ = new Subject<PersonalDashboardSummary>();
      mockApi.getPersonalSummary.and.returnValue(delayedPersonal$);

      store.scope.set('PERSONAL');

      // Call twice concurrently
      let res1: any;
      let res2: any;
      store.loadPersonalSummary().subscribe((r) => (res1 = r));
      store.loadPersonalSummary().subscribe((r) => (res2 = r));

      // Must call backend API only ONCE
      expect(mockApi.getPersonalSummary).toHaveBeenCalledTimes(1);

      // Complete the single in-flight request
      delayedPersonal$.next(samplePersonalSummary);
      delayedPersonal$.complete();

      expect(res1).toEqual(samplePersonalSummary);
      expect(res2).toEqual(samplePersonalSummary);
      expect(store.personalSummary()).toEqual(samplePersonalSummary);
    });

    it('should deduplicate concurrent in-flight requests for the same organization', () => {
      const delayedOrg$ = new Subject<OrganizationDashboardSummary>();
      mockApi.getOrganizationSummary.and.returnValue(delayedOrg$);

      store.scope.set('ORGANIZATION');
      mockWorkspaceContext.activeOrgCode.set('CORP1');

      // Call twice concurrently for CORP1
      let res1: any;
      let res2: any;
      store.loadOrganizationSummary('CORP1').subscribe((r) => (res1 = r));
      store.loadOrganizationSummary('CORP1').subscribe((r) => (res2 = r));

      // Must call backend API only ONCE
      expect(mockApi.getOrganizationSummary).toHaveBeenCalledTimes(1);

      // Complete the request
      delayedOrg$.next(sampleOrgSummary);
      delayedOrg$.complete();

      expect(res1).toEqual(sampleOrgSummary);
      expect(res2).toEqual(sampleOrgSummary);
      expect(store.organizationSummary()).toEqual(sampleOrgSummary);
    });

    it('should force a fresh request on forceRefresh even if already loaded or in-flight', () => {
      store.personalSummary.set(samplePersonalSummary);

      store.loadPersonalSummary(true).subscribe();

      expect(mockApi.getPersonalSummary).toHaveBeenCalledTimes(1);
      expect(store.isRefreshing()).toBeFalse();
    });

    it('should deduplicate reactive context changes when context signals emit without an effective change', () => {
      store.autoSyncEnabled.set(true);

      mockWorkspaceContext.isPersonal.set(false);
      mockWorkspaceContext.activeOrgCode.set('CORP1');
      TestBed.flushEffects();

      expect(mockApi.getOrganizationSummary).toHaveBeenCalledTimes(1);

      // Re-emit with the same orgCode (e.g. workspace selection changed inside the same org)
      mockWorkspaceContext.activeOrgCode.set('CORP1');
      TestBed.flushEffects();

      // Must NOT dispatch a duplicate HTTP call
      expect(mockApi.getOrganizationSummary).toHaveBeenCalledTimes(1);
    });
  });

  describe('State Reset', () => {
    it('should reset all state back to initial values', () => {
      store.personalSummary.set(samplePersonalSummary);
      store.organizationSummary.set(sampleOrgSummary);
      store.scope.set('ORGANIZATION');
      store.error.set('Something went wrong');

      store.reset();

      expect(store.scope()).toBe('PERSONAL');
      expect(store.personalSummary()).toBeNull();
      expect(store.organizationSummary()).toBeNull();
      expect(store.error()).toBeNull();
      expect(store.lastLoadedAt()).toBeNull();
    });
  });
});
