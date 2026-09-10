import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { PrivacySettingsComponent } from './privacy-settings.component';
import { SettingsApiService } from '../../services/settings-api.service';
import { WorkspaceContextStore } from '../../../../state/workspace/workspace-context.store';
import { WorkspacePolicyDto } from '../../interfaces/settings.interfaces';
import { OrganizationModel } from '../../../../core/models/organization.model';
import { ProjectWorkspaceModel } from '../../../../core/models/project-workspace.model';

describe('PrivacySettingsComponent — Policy Target Contract', () => {
  let component: PrivacySettingsComponent;
  let mockSettingsApi: jasmine.SpyObj<SettingsApiService>;

  const activeOrgSignal = signal<OrganizationModel | null>(null);
  const activeWsSignal = signal<ProjectWorkspaceModel | null>(null);

  const mockPolicy: WorkspacePolicyDto = {
    workspaceType: 'PERSONAL',
    externalSharingMode: 'OFF',
    discoverabilityMode: 'PRIVATE',
    allowAccessRequests: true,
    requireRequestReason: false,
    allowExternalAccessRequests: false,
    reviewerScopePolicy: 'RESOURCE_ADMINS_ONLY'
  };

  const mockWorkspaceContext = {
    isPersonal: () => !activeOrgSignal(),
    isOrganization: () => !!activeOrgSignal(),
    activeOrgCode: () => activeOrgSignal()?.orgCode ?? null,
    activeOrganization: () => activeOrgSignal(),
    activeWorkspace: () => activeWsSignal(),
  };

  beforeEach(() => {
    activeOrgSignal.set(null);
    activeWsSignal.set(null);

    mockSettingsApi = jasmine.createSpyObj('SettingsApiService', [
      'getPrivacySettings',
      'getWorkspacePolicy',
      'updateWorkspacePolicy',
      'updatePrivacySettings'
    ]);

    mockSettingsApi.getPrivacySettings.and.returnValue(of({
      allowInternalDms: true,
      allowExternalDms: true,
      readReceiptsEnabled: true
    }));

    mockSettingsApi.getWorkspacePolicy.and.returnValue(of(mockPolicy));
    mockSettingsApi.updateWorkspacePolicy.and.returnValue(of(mockPolicy));

    TestBed.configureTestingModule({
      imports: [PrivacySettingsComponent],
      providers: [
        { provide: SettingsApiService, useValue: mockSettingsApi },
        { provide: WorkspaceContextStore, useValue: mockWorkspaceContext }
      ]
    });

    const fixture = TestBed.createComponent(PrivacySettingsComponent);
    component = fixture.componentInstance;
  });

  it('A. should resolve policy target to "personal" in Personal Space', () => {
    activeOrgSignal.set(null);
    activeWsSignal.set({
      id: 10,
      organizationCode: null,
      name: 'Personal Space',
      rootFolderId: 100,
      status: 'ACTIVE'
    });

    expect(component.activePolicyTarget()).toBe('personal');
    component.ngOnInit();
    expect(mockSettingsApi.getWorkspacePolicy).toHaveBeenCalledWith('personal');
    expect(mockSettingsApi.getWorkspacePolicy).not.toHaveBeenCalledWith(10 as any);
  });

  it('B. should resolve policy target to active orgCode in Organization Space', () => {
    activeOrgSignal.set({
      orgCode: 'ACME_CORP',
      name: 'Acme Corporation'
    } as OrganizationModel);

    activeWsSignal.set({
      id: 42,
      organizationCode: 'ACME_CORP',
      name: 'Engineering Workspace',
      rootFolderId: 400,
      status: 'ACTIVE'
    });

    expect(component.activePolicyTarget()).toBe('ACME_CORP');
    component.ngOnInit();
    expect(mockSettingsApi.getWorkspacePolicy).toHaveBeenCalledWith('ACME_CORP');
    expect(mockSettingsApi.getWorkspacePolicy).not.toHaveBeenCalledWith(42 as any);
  });

  it('C. should NEVER pass numeric workspace ID when updating policy', () => {
    activeOrgSignal.set(null);
    activeWsSignal.set({
      id: 10,
      organizationCode: null,
      name: 'Personal Space',
      rootFolderId: 100,
      status: 'ACTIVE'
    });

    component.ngOnInit();
    component.updateWorkspacePolicy('externalSharingMode', 'ON');

    expect(mockSettingsApi.updateWorkspacePolicy).toHaveBeenCalledWith(
      'personal',
      jasmine.objectContaining({ externalSharingMode: 'ON' })
    );
    expect(mockSettingsApi.updateWorkspacePolicy).not.toHaveBeenCalledWith(10 as any, jasmine.anything());
  });

  it('D. should maintain same policy target when switching workspaces inside same organization', () => {
    activeOrgSignal.set({
      orgCode: 'ACME_CORP',
      name: 'Acme Corporation'
    } as OrganizationModel);

    activeWsSignal.set({
      id: 42,
      organizationCode: 'ACME_CORP',
      name: 'Workspace 1',
      rootFolderId: 400,
      status: 'ACTIVE'
    });

    expect(component.activePolicyTarget()).toBe('ACME_CORP');

    // Switch to Workspace 2 within same organization
    activeWsSignal.set({
      id: 43,
      organizationCode: 'ACME_CORP',
      name: 'Workspace 2',
      rootFolderId: 401,
      status: 'ACTIVE'
    });

    expect(component.activePolicyTarget()).toBe('ACME_CORP');
  });
});
