import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TeamOverviewTabComponent } from './team-overview-tab.component';
import { GroupResponseDto, OrganizationMemberResponseDto } from '../../../../../core/models/organization.model';
import { TeamAccessBoundaryDto } from '../../../models/team-delegation.models';

describe('TeamOverviewTabComponent', () => {
  let component: TeamOverviewTabComponent;
  let fixture: ComponentFixture<TeamOverviewTabComponent>;

  const mockTeam: GroupResponseDto = {
    id: 1,
    orgCode: 'ORG1',
    name: 'Engineering Team',
    description: 'Core infrastructure and feature development',
    status: 'ACTIVE',
    memberCount: 8,
    createdAt: '2026-09-08T10:00:00Z',
    updatedAt: '2026-09-08T10:00:00Z',
  };

  const mockManager: OrganizationMemberResponseDto = {
    userId: 'mgr-1',
    email: 'bob@example.com',
    firstName: 'Bob',
    lastName: 'Builder',
    role: 'ROLE_USER',
    groupRole: 'MANAGER',
    joinedAt: '2026-09-08T10:00:00Z',
  };

  const mockBoundary: TeamAccessBoundaryDto = {
    teamId: 1,
    teamName: 'Engineering Team',
    workspaceId: 3,
    workspaceName: 'Engineering Workspace',
    rootFolderId: 12,
    rootFolderName: 'Src',
    rootFolderPath: '/Src',
    permissionLevel: 'EDIT',
    permissionType: 'BOUNDARY',
    status: 'ACTIVE',
    assignedAt: '2026-09-08T10:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TeamOverviewTabComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TeamOverviewTabComponent);
    component = fixture.componentInstance;
  });

  it('should create the component', () => {
    fixture.componentRef.setInput('team', mockTeam);
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should display team identity and status', () => {
    fixture.componentRef.setInput('team', mockTeam);
    fixture.componentRef.setInput('myRole', 'Team Manager');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const nameEl = compiled.querySelector('.team-name');
    expect(nameEl?.textContent?.trim()).toBe('Engineering Team');

    const descEl = compiled.querySelector('.team-desc');
    expect(descEl?.textContent).toContain('Core infrastructure');
  });

  it('should display manager information in the KPI card', () => {
    fixture.componentRef.setInput('team', mockTeam);
    fixture.componentRef.setInput('manager', mockManager);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const mgrName = compiled.querySelector('.manager-name');
    expect(mgrName?.textContent).toContain('Bob Builder');

    const mgrEmail = compiled.querySelector('.manager-email');
    expect(mgrEmail?.textContent).toContain('bob@example.com');
  });

  it('should emit tabChange when clicking KPI cards', () => {
    fixture.componentRef.setInput('team', mockTeam);
    fixture.detectChanges();

    spyOn(component.tabChange, 'emit');

    const compiled = fixture.nativeElement as HTMLElement;
    const kpiCards = compiled.querySelectorAll('.kpi-card.clickable');
    expect(kpiCards.length).toBeGreaterThanOrEqual(3);

    // Click members card
    (kpiCards[0] as HTMLElement).click();
    expect(component.tabChange.emit).toHaveBeenCalledWith('MEMBERS');
  });

  it('should show zero default content access note in workspace snapshot', () => {
    fixture.componentRef.setInput('team', mockTeam);
    fixture.componentRef.setInput('boundary', mockBoundary);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const reminder = compiled.querySelector('.zero-access-reminder');
    expect(reminder?.textContent).toContain('Zero-Default Content Access');
  });
});
