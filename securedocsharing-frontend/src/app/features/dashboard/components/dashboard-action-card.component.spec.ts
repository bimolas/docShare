import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { DashboardActionCardComponent } from './dashboard-action-card.component';
import {
  PersonalDashboardSummary,
  OrganizationDashboardSummary,
} from '../models/dashboard.model';

describe('DashboardActionCardComponent', () => {
  let component: DashboardActionCardComponent;
  let fixture: ComponentFixture<DashboardActionCardComponent>;

  const samplePersonalSummary: PersonalDashboardSummary = {
    usedStorageBytes: 100,
    storageLimitBytes: 1000,
    storageUsagePercentage: 10,
    usedStorageFormatted: '100 B',
    documentCount: 5,
    folderCount: 1,
    pendingAccessReviewsCount: 2,
    myPendingAccessRequestsCount: 1,
    activeDirectSharesCount: 3,
    recentActivityTrend: [],
  };

  const sampleOrgSummary: OrganizationDashboardSummary = {
    orgCode: 'CORP1',
    orgName: 'Corp Global',
    usedStorageBytes: 2000,
    storageLimitBytes: 10000,
    storageUsagePercentage: 20,
    usedStorageFormatted: '2 KB',
    documentCount: 50,
    folderCount: 10,
    workspaceCount: 2,
    teamCount: 3,
    memberCount: 12,
    pendingAccessRequestsCount: 4,
    pendingInvitationsCount: 1,
    recentActivityTrend: [],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardActionCardComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardActionCardComponent);
    component = fixture.componentInstance;
  });

  it('should render Personal Space actions when scope is PERSONAL', () => {
    fixture.componentRef.setInput('scope', 'PERSONAL');
    fixture.componentRef.setInput('personalSummary', samplePersonalSummary);
    fixture.detectChanges();

    const titleEl = fixture.debugElement.query(By.css('h3'));
    expect(titleEl.nativeElement.textContent.trim()).toBe('Personal Actions');

    const reviewsEl = fixture.debugElement.query(By.css('.bg-amber-50\\/70'));
    expect(reviewsEl.nativeElement.textContent).toContain('Access Reviews Required');
    expect(reviewsEl.nativeElement.textContent).toContain('2');
  });

  it('should render Organization Space actions when scope is ORGANIZATION', () => {
    fixture.componentRef.setInput('scope', 'ORGANIZATION');
    fixture.componentRef.setInput('organizationSummary', sampleOrgSummary);
    fixture.componentRef.setInput('orgCode', 'CORP1');
    fixture.detectChanges();

    const titleEl = fixture.debugElement.query(By.css('h3'));
    expect(titleEl.nativeElement.textContent.trim()).toBe('Organization Administration');

    const requestsEl = fixture.debugElement.query(By.css('.bg-amber-50\\/70'));
    expect(requestsEl.nativeElement.textContent).toContain('Access Requests');
    expect(requestsEl.nativeElement.textContent).toContain('4');
  });

  it('should render all-clear badge when no pending actions exist in Personal', () => {
    fixture.componentRef.setInput('scope', 'PERSONAL');
    fixture.componentRef.setInput('personalSummary', {
      ...samplePersonalSummary,
      pendingAccessReviewsCount: 0,
      myPendingAccessRequestsCount: 0,
      activeDirectSharesCount: 0,
    });
    fixture.detectChanges();

    const clearEl = fixture.debugElement.query(By.css('.bg-emerald-50\\/60'));
    expect(clearEl).toBeTruthy();
    expect(clearEl.nativeElement.textContent).toContain('All Clear');
  });
});
