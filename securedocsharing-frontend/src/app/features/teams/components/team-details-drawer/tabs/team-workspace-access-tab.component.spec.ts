import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TeamWorkspaceAccessTabComponent } from './team-workspace-access-tab.component';
import { TeamAccessBoundaryDto } from '../../../models/team-delegation.models';

describe('TeamWorkspaceAccessTabComponent', () => {
  let component: TeamWorkspaceAccessTabComponent;
  let fixture: ComponentFixture<TeamWorkspaceAccessTabComponent>;

  const mockBoundary: TeamAccessBoundaryDto = {
    permissionId: 55,
    teamId: 1,
    teamName: 'Engineering',
    workspaceId: 10,
    workspaceName: 'Engineering Workspace',
    folderId: 101,
    folderName: 'Architecture',
    folderPath: '/Architecture',
    ceilingLevel: 'EDIT',
    permissionLevel: 'EDIT',
    status: 'ACTIVE',
    allocatedAt: '2026-09-08T10:00:00Z',
    allocatedByUserEmail: 'admin@example.com',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TeamWorkspaceAccessTabComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TeamWorkspaceAccessTabComponent);
    component = fixture.componentInstance;
  });

  it('should create the component', () => {
    fixture.componentRef.setInput('boundary', mockBoundary);
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should display workspace name, folder path and maximum permission badge', () => {
    fixture.componentRef.setInput('boundary', mockBoundary);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const nameEl = compiled.querySelector('.workspace-name');
    expect(nameEl?.textContent?.trim()).toBe('Engineering Workspace');

    const pathVal = compiled.querySelector('.path-value');
    expect(pathVal?.textContent?.trim()).toBe('/Architecture');

    const permBadge = compiled.querySelector('.perm-badge');
    expect(permBadge?.textContent).toContain('Maximum: EDIT');
  });

  it('should show empty state when boundary is null', () => {
    fixture.componentRef.setInput('boundary', null);
    fixture.componentRef.setInput('canManageOrg', false);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const emptyState = compiled.querySelector('.unallocated-card');
    expect(emptyState).toBeTruthy();
    expect(emptyState?.textContent).toContain('No Workspace Allocated');
  });

  it('should emit allocateWorkspace when clicking allocate button in empty state', () => {
    fixture.componentRef.setInput('boundary', null);
    fixture.componentRef.setInput('canManageOrg', true);
    fixture.componentRef.setInput('isTeamActive', true);
    fixture.detectChanges();

    spyOn(component.allocateWorkspace, 'emit');

    const compiled = fixture.nativeElement as HTMLElement;
    const allocateBtn = compiled.querySelector('.btn-allocate-empty') as HTMLButtonElement;
    expect(allocateBtn).toBeTruthy();
    allocateBtn.click();

    expect(component.allocateWorkspace.emit).toHaveBeenCalled();
  });

  it('should emit changeWorkspace when clicking Change Access button', () => {
    fixture.componentRef.setInput('boundary', mockBoundary);
    fixture.componentRef.setInput('canManageOrg', true);
    fixture.componentRef.setInput('isTeamActive', true);
    fixture.detectChanges();

    spyOn(component.changeWorkspace, 'emit');

    const compiled = fixture.nativeElement as HTMLElement;
    const changeBtn = compiled.querySelector('.btn-change') as HTMLButtonElement;
    expect(changeBtn).toBeTruthy();
    changeBtn.click();

    expect(component.changeWorkspace.emit).toHaveBeenCalled();
  });

  it('should toggle confirm revoke popover and emit revokeWorkspace on confirm', () => {
    fixture.componentRef.setInput('boundary', mockBoundary);
    fixture.componentRef.setInput('canManageOrg', true);
    fixture.componentRef.setInput('isTeamActive', true);
    fixture.detectChanges();

    spyOn(component.revokeWorkspace, 'emit');

    const compiled = fixture.nativeElement as HTMLElement;
    const revokeBtn = compiled.querySelector('.btn-revoke') as HTMLButtonElement;
    expect(revokeBtn).toBeTruthy();
    revokeBtn.click();
    fixture.detectChanges();

    expect(component.confirmRevoke()).toBeTrue();
    const yesBtn = compiled.querySelector('.btn-pop-yes') as HTMLButtonElement;
    expect(yesBtn).toBeTruthy();
    yesBtn.click();

    expect(component.revokeWorkspace.emit).toHaveBeenCalled();
  });
});
