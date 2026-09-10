import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TeamCardComponent } from './team-card.component';
import { GroupResponseDto, OrganizationMemberResponseDto } from '../../../../core/models/organization.model';
import { TeamAccessBoundaryDto } from '../../models/team-delegation.models';

describe('TeamCardComponent', () => {
  let component: TeamCardComponent;
  let fixture: ComponentFixture<TeamCardComponent>;

  const mockGroup: GroupResponseDto = {
    id: 1,
    orgCode: 'ORG1',
    name: 'Finance Team',
    description: 'Handles financial reports and audits',
    status: 'ACTIVE',
    memberCount: 5,
    createdAt: '2026-09-08T10:00:00Z',
    updatedAt: '2026-09-08T10:00:00Z',
  };

  const mockManager: OrganizationMemberResponseDto = {
    userId: 'user-mgr-1',
    email: 'alice@finance.com',
    firstName: 'Alice',
    lastName: 'Stone',
    role: 'ROLE_USER',
    groupRole: 'MANAGER',
    joinedAt: '2026-09-08T10:00:00Z',
  };

  const mockBoundary: TeamAccessBoundaryDto = {
    teamId: 1,
    teamName: 'Finance Team',
    workspaceId: 5,
    workspaceName: 'Finance Workspace',
    rootFolderId: 20,
    rootFolderName: 'Accounting',
    rootFolderPath: '/Accounting',
    permissionLevel: 'EDIT',
    permissionType: 'BOUNDARY',
    status: 'ACTIVE',
    assignedAt: '2026-09-08T10:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TeamCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TeamCardComponent);
    component = fixture.componentInstance;
  });

  it('should create the component', () => {
    fixture.componentRef.setInput('group', mockGroup);
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should compute teamInitials correctly', () => {
    fixture.componentRef.setInput('group', mockGroup);
    fixture.detectChanges();
    expect(component.teamInitials()).toBe('FT');
  });

  it('should display "Team Manager" when myRole is Team Manager', () => {
    fixture.componentRef.setInput('group', mockGroup);
    fixture.componentRef.setInput('myRole', 'Team Manager');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const rolePill = compiled.querySelector('.pill-manager');
    expect(rolePill).toBeTruthy();
    expect(rolePill?.textContent?.trim()).toBe('Team Manager');
  });

  it('should display "Member" when myRole is Member', () => {
    fixture.componentRef.setInput('group', mockGroup);
    fixture.componentRef.setInput('myRole', 'Member');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const rolePill = compiled.querySelector('.pill-member');
    expect(rolePill).toBeTruthy();
    expect(rolePill?.textContent?.trim()).toBe('Member');
  });

  it('should display "—" when user is not in the team', () => {
    fixture.componentRef.setInput('group', mockGroup);
    fixture.componentRef.setInput('myRole', '—');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const emptyRole = compiled.querySelector('.role-empty');
    expect(emptyRole).toBeTruthy();
    expect(emptyRole?.textContent?.trim()).toBe('—');
  });

  it('should display "Manage Team" if user is Team Manager', () => {
    fixture.componentRef.setInput('group', mockGroup);
    fixture.componentRef.setInput('myRole', 'Team Manager');
    fixture.componentRef.setInput('canManage', false);
    fixture.detectChanges();

    expect(component.canManageTeam()).toBeTrue();
    const compiled = fixture.nativeElement as HTMLElement;
    const manageBtn = compiled.querySelector('.btn-manage-row');
    expect(manageBtn?.textContent).toContain('Manage Team');
  });

  it('should display "View Team" if user is an ordinary member with no org admin rights', () => {
    fixture.componentRef.setInput('group', mockGroup);
    fixture.componentRef.setInput('myRole', 'Member');
    fixture.componentRef.setInput('canManage', false);
    fixture.detectChanges();

    expect(component.canManageTeam()).toBeFalse();
    const compiled = fixture.nativeElement as HTMLElement;
    const manageBtn = compiled.querySelector('.btn-manage-row');
    expect(manageBtn?.textContent).toContain('View Team');
  });

  it('should display workspace name and Maximum permission badge when allocated', () => {
    fixture.componentRef.setInput('group', mockGroup);
    fixture.componentRef.setInput('boundary', mockBoundary);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const wsName = compiled.querySelector('.workspace-name');
    expect(wsName?.textContent?.trim()).toBe('Finance Workspace');

    const permBadge = compiled.querySelector('.perm-badge');
    expect(permBadge?.textContent).toContain('Maximum: EDIT');
  });

  it('should display unallocated badge when boundary is null', () => {
    fixture.componentRef.setInput('group', mockGroup);
    fixture.componentRef.setInput('boundary', null);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const unallocated = compiled.querySelector('.unallocated-workspace');
    expect(unallocated?.textContent).toContain('No workspace allocated');
  });
});
