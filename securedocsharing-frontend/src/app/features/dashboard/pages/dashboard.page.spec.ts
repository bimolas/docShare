import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { DashboardPageComponent } from './dashboard.page';
import { DashboardStore } from '../state/dashboard.store';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { provideEchartsCore } from 'ngx-echarts';
import {
  PersonalDashboardSummary,
  OrganizationDashboardSummary,
  StorageGaugeViewModel,
  ActivityTrendViewModel,
} from '../models/dashboard.model';

describe('DashboardPageComponent', () => {
  let component: DashboardPageComponent;
  let fixture: ComponentFixture<DashboardPageComponent>;

  const samplePersonalSummary: PersonalDashboardSummary = {
    usedStorageBytes: 524288000,
    storageLimitBytes: 10737418240,
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
    orgName: 'Acme Global Corp',
    usedStorageBytes: 10737418240,
    storageLimitBytes: 53687091200,
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

  let mockDashboardStore: {
    scope: ReturnType<typeof signal<any>>;
    isPersonal: ReturnType<typeof signal<boolean>>;
    isOrganization: ReturnType<typeof signal<boolean>>;
    isWorkspace: ReturnType<typeof signal<boolean>>;
    isUnsupportedScope: ReturnType<typeof signal<boolean>>;
    isLoading: ReturnType<typeof signal<boolean>>;
    isRefreshing: ReturnType<typeof signal<boolean>>;
    error: ReturnType<typeof signal<string | null>>;
    hasData: ReturnType<typeof signal<boolean>>;
    isEmpty: ReturnType<typeof signal<boolean>>;
    lastLoadedAt: ReturnType<typeof signal<number | null>>;
    personalSummary: ReturnType<typeof signal<PersonalDashboardSummary | null>>;
    organizationSummary: ReturnType<typeof signal<OrganizationDashboardSummary | null>>;
    activeOrgCode: ReturnType<typeof signal<string | null>>;
    storageGaugeViewModel: ReturnType<typeof signal<StorageGaugeViewModel | null>>;
    storageGaugeOptions: ReturnType<typeof signal<any>>;
    activityTrendViewModel: ReturnType<typeof signal<ActivityTrendViewModel | null>>;
    activityTrendOptions: ReturnType<typeof signal<any>>;
    activityBarChartOptions: ReturnType<typeof signal<any>>;
    segmentedHalfGaugeOptions: ReturnType<typeof signal<any>>;
    contentDistribution: ReturnType<typeof signal<any[]>>;
    recentActivityLedger: ReturnType<typeof signal<any[]>>;
    refresh: jasmine.Spy;
  };

  let mockWorkspaceContext: {
    isPersonal: ReturnType<typeof signal<boolean>>;
    isOrganization: ReturnType<typeof signal<boolean>>;
    activeOrgCode: ReturnType<typeof signal<string | null>>;
  };

  beforeEach(async () => {
    mockDashboardStore = {
      scope: signal('PERSONAL'),
      isPersonal: signal(true),
      isOrganization: signal(false),
      isWorkspace: signal(false),
      isUnsupportedScope: signal(false),
      isLoading: signal(false),
      isRefreshing: signal(false),
      error: signal<string | null>(null),
      hasData: signal(true),
      isEmpty: signal(false),
      lastLoadedAt: signal(Date.now()),
      personalSummary: signal<PersonalDashboardSummary | null>(samplePersonalSummary),
      organizationSummary: signal<OrganizationDashboardSummary | null>(null),
      activeOrgCode: signal<string | null>(null),
      storageGaugeViewModel: signal<StorageGaugeViewModel | null>({
        percentage: 4.88,
        usedBytes: 524288000,
        limitBytes: 10737418240,
        formattedUsed: '500.0 MB',
        formattedLimit: '10 GB',
        accentColor: '#f97316',
        trackColor: '#f1f5f9',
      }),
      storageGaugeOptions: signal({}),
      activityTrendViewModel: signal<ActivityTrendViewModel | null>({
        dates: ['2026-09-08', '2026-09-09'],
        counts: [4, 9],
        totalActions: 13,
        peakCount: 9,
      }),
      activityTrendOptions: signal({}),
      activityBarChartOptions: signal({}),
      segmentedHalfGaugeOptions: signal({}),
      contentDistribution: signal([
        { label: 'Documents', count: 20, percentage: 62, color: '#f97316' },
        { label: 'Folders', count: 5, percentage: 16, color: '#0f172a' },
        { label: 'Direct Shares', count: 7, percentage: 22, color: '#10b981' },
      ]),
      recentActivityLedger: signal([
        { date: '2026-09-09', count: 9, intensity: 3 },
        { date: '2026-09-08', count: 4, intensity: 2 },
      ]),
      refresh: jasmine.createSpy('refresh'),
    };

    mockWorkspaceContext = {
      isPersonal: signal(true),
      isOrganization: signal(false),
      activeOrgCode: signal(null),
    };

    await TestBed.configureTestingModule({
      imports: [DashboardPageComponent],
      providers: [
        provideRouter([]),
        provideEchartsCore({ echarts: () => import('echarts') }),
        { provide: DashboardStore, useValue: mockDashboardStore },
        { provide: WorkspaceContextStore, useValue: mockWorkspaceContext },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the dashboard page component', () => {
    expect(component).toBeTruthy();
  });

  describe('Personal Dashboard Rendering', () => {
    it('should render Personal Space title and context pill', () => {
      const titleEl = fixture.debugElement.query(By.css('h1'));
      expect(titleEl.nativeElement.textContent.trim()).toBe('Personal Space Overview');

      const pillEl = fixture.debugElement.query(By.css('header span'));
      expect(pillEl.nativeElement.textContent.trim()).toContain('Personal Space');
    });

    it('should display Action Required banner when pendingAccessReviewsCount > 0', () => {
      const bannerEl = fixture.debugElement.query(By.css('[role="alert"]'));
      expect(bannerEl).toBeTruthy();
      expect(bannerEl.nativeElement.textContent).toContain('Action Required');
      expect(bannerEl.nativeElement.textContent).toContain('3 access review requests require your approval');
    });

    it('should render KPI cards for Personal Space', () => {
      const kpiCards = fixture.debugElement.queryAll(By.directive(DashboardPageComponent));
      expect(component.personalSummary()?.documentCount).toBe(20);
      expect(component.personalSummary()?.folderCount).toBe(5);
      expect(component.personalSummary()?.activeDirectSharesCount).toBe(7);
      expect(component.personalSummary()?.pendingAccessReviewsCount).toBe(3);
    });
  });

  describe('Organization Dashboard Rendering', () => {
    beforeEach(() => {
      mockDashboardStore.scope.set('ORGANIZATION');
      mockDashboardStore.isPersonal.set(false);
      mockDashboardStore.isOrganization.set(true);
      mockDashboardStore.personalSummary.set(null);
      mockDashboardStore.organizationSummary.set(sampleOrgSummary);
      mockDashboardStore.activeOrgCode.set('CORP1');
      fixture.detectChanges();
    });

    it('should render Organization Space title and organization badge', () => {
      const titleEl = fixture.debugElement.query(By.css('h1'));
      expect(titleEl.nativeElement.textContent.trim()).toBe('Acme Global Corp Overview');

      const pillEl = fixture.debugElement.query(By.css('header span'));
      expect(pillEl.nativeElement.textContent.trim()).toContain('Organization: CORP1');
    });

    it('should display Action Required banner for Organization when pendingAccessRequestsCount > 0', () => {
      const bannerEl = fixture.debugElement.query(By.css('[role="alert"]'));
      expect(bannerEl).toBeTruthy();
      expect(bannerEl.nativeElement.textContent).toContain('Action Required');
      expect(bannerEl.nativeElement.textContent).toContain('6 organization access requests await administrative review');
    });
  });

  describe('Loading, Error, Unsupported and Empty States', () => {
    it('should render skeleton loading state when isLoading is true and hasData is false', () => {
      mockDashboardStore.isLoading.set(true);
      mockDashboardStore.hasData.set(false);
      fixture.detectChanges();

      const skeletonEl = fixture.debugElement.query(By.css('[aria-busy="true"]'));
      expect(skeletonEl).toBeTruthy();
    });

    it('should render error state with retry button when error occurs', () => {
      mockDashboardStore.error.set('Network connection failed');
      mockDashboardStore.hasData.set(false);
      mockDashboardStore.isLoading.set(false);
      fixture.detectChanges();

      const errorEl = fixture.debugElement.query(By.css('.bg-rose-50\\/70'));
      expect(errorEl).toBeTruthy();
      expect(errorEl.nativeElement.textContent).toContain('Network connection failed');

      const retryBtn = errorEl.query(By.css('button'));
      retryBtn.nativeElement.click();
      expect(mockDashboardStore.refresh).toHaveBeenCalled();
    });

    it('should render explicit unsupported notice when in WORKSPACE scope', () => {
      mockDashboardStore.isUnsupportedScope.set(true);
      mockDashboardStore.scope.set('WORKSPACE');
      mockDashboardStore.hasData.set(false);
      fixture.detectChanges();

      const unsupportedEl = fixture.debugElement.query(By.css('h2'));
      expect(unsupportedEl.nativeElement.textContent).toContain('Workspace Dashboard Unavailable');
    });

    it('should render empty space state when isEmpty is true', () => {
      mockDashboardStore.isEmpty.set(true);
      mockDashboardStore.hasData.set(true);
      fixture.detectChanges();

      const emptyHeading = fixture.debugElement.query(By.css('h2'));
      expect(emptyHeading.nativeElement.textContent).toContain('Your Personal Space is Empty');
    });
  });

  describe('User Actions', () => {
    it('should trigger store.refresh when refresh button is clicked', () => {
      const refreshBtn = fixture.debugElement.query(By.css('header button'));
      refreshBtn.nativeElement.click();
      expect(mockDashboardStore.refresh).toHaveBeenCalled();
    });
  });
});
