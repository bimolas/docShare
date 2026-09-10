import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DashboardApiService } from './dashboard-api.service';
import { environment } from '../../../../environments/environment';
import {

  PersonalDashboardSummary,
  OrganizationDashboardSummary,
} from '../models/dashboard.model';

describe('DashboardApiService', () => {
  let service: DashboardApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        DashboardApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(DashboardApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should fetch personal dashboard summary from /api/v1/analytics/dashboard/personal', () => {
    const mockPersonalSummary: PersonalDashboardSummary = {
      usedStorageBytes: 104857600,
      storageLimitBytes: 10737418240,
      storageUsagePercentage: 0.98,
      usedStorageFormatted: '100.0 MB',
      documentCount: 15,
      folderCount: 4,
      pendingAccessReviewsCount: 2,
      myPendingAccessRequestsCount: 1,
      activeDirectSharesCount: 6,
      recentActivityTrend: [
        { date: '2026-09-08', count: 5, intensity: 2 },
        { date: '2026-09-09', count: 8, intensity: 3 },
      ],
    };

    service.getPersonalSummary().subscribe((data) => {
      expect(data).toEqual(mockPersonalSummary);
      expect(data.documentCount).toBe(15);
      expect(data.usedStorageFormatted).toBe('100.0 MB');
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/analytics/dashboard/personal`);
    expect(req.request.method).toBe('GET');
    req.flush(mockPersonalSummary);
  });

  it('should fetch organization dashboard summary with orgCode parameter', () => {
    const mockOrgSummary: OrganizationDashboardSummary = {
      orgCode: 'ACME',
      orgName: 'Acme Corporation',
      usedStorageBytes: 5368709120,
      storageLimitBytes: 53687091200,
      storageUsagePercentage: 10.0,
      usedStorageFormatted: '5.0 GB',
      documentCount: 120,
      folderCount: 22,
      workspaceCount: 5,
      teamCount: 3,
      memberCount: 28,
      pendingAccessRequestsCount: 4,
      pendingInvitationsCount: 2,
      recentActivityTrend: [
        { date: '2026-09-08', count: 12, intensity: 3 },
        { date: '2026-09-09', count: 19, intensity: 4 },
      ],
    };

    service.getOrganizationSummary('ACME').subscribe((data) => {
      expect(data).toEqual(mockOrgSummary);
      expect(data.orgCode).toBe('ACME');
      expect(data.workspaceCount).toBe(5);
      expect(data.memberCount).toBe(28);
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/analytics/dashboard/organization?orgCode=ACME`);
    expect(req.request.method).toBe('GET');
    req.flush(mockOrgSummary);
  });

  it('should trim orgCode parameter before making HTTP call', () => {
    service.getOrganizationSummary('  ACME  ').subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/analytics/dashboard/organization?orgCode=ACME`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});

