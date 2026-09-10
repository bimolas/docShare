import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { WorkspaceContextStore } from './workspace-context.store';
import { OrganizationApiService } from '../../core/services/organization-api.service';
import { WorkspaceApiService } from '../../core/services/workspace-api.service';
import { SubspacePersistenceService } from '../../core/services/subspace-persistence.service';
import { AuthStore } from '../auth/auth.store';
import { OrganizationResponseDto } from '../../core/models/organization.model';
import { ProjectWorkspaceModel } from '../../core/models/project-workspace.model';

describe('WorkspaceContextStore', () => {
  let store: WorkspaceContextStore;
  let mockOrgApi: jasmine.SpyObj<OrganizationApiService>;
  let mockWorkspaceApi: jasmine.SpyObj<WorkspaceApiService>;
  let mockPersistence: jasmine.SpyObj<SubspacePersistenceService>;
  let mockAuthStore: jasmine.SpyObj<AuthStore>;

  const mockOrgs: OrganizationResponseDto[] = [
    { orgCode: 'ORG_1', name: 'Acme Corp', createdAt: '2026-01-01T00:00:00Z' },
    { orgCode: 'ORG_2', name: 'Beta Inc', createdAt: '2026-01-01T00:00:00Z' },
  ];

  const mockOrgWorkspaces: ProjectWorkspaceModel[] = [
    {
      id: 42,
      organizationCode: 'ORG_1',
      name: 'General',
      rootFolderId: 101,
      rootFolderName: 'General Root',
      status: 'ACTIVE',
      isDefault: true,
    },
    {
      id: 43,
      organizationCode: 'ORG_1',
      name: 'Engineering',
      rootFolderId: 102,
      rootFolderName: 'Engineering Root',
      status: 'ACTIVE',
      isDefault: false,
    },
    {
      id: 44,
      organizationCode: 'ORG_1',
      name: 'Archived Space',
      rootFolderId: 103,
      rootFolderName: 'Archived Root',
      status: 'ARCHIVED',
      isDefault: false,
    },
  ];

  const mockPersonalWorkspaces: ProjectWorkspaceModel[] = [
    {
      id: 10,
      organizationCode: null,
      name: 'My Personal Notes',
      rootFolderId: 200,
      rootFolderName: 'My Personal Notes',
      status: 'ACTIVE',
      isDefault: true,
    },
    {
      id: 11,
      organizationCode: null,
      name: 'Side Project',
      rootFolderId: 201,
      rootFolderName: 'Side Project',
      status: 'ACTIVE',
      isDefault: false,
    },
    {
      id: 12,
      organizationCode: null,
      name: 'Archived Personal',
      rootFolderId: 202,
      rootFolderName: 'Archived Personal',
      status: 'ARCHIVED',
      isDefault: false,
    },
  ];

  beforeEach(() => {
    mockOrgApi = jasmine.createSpyObj<OrganizationApiService>('OrganizationApiService', ['getUserOrganizations']);
    mockWorkspaceApi = jasmine.createSpyObj<WorkspaceApiService>('WorkspaceApiService', [
      'getWorkspacesForOrganization',
      'getPersonalWorkspaces',
      'createPersonalWorkspace',
    ]);
    mockPersistence = jasmine.createSpyObj<SubspacePersistenceService>('SubspacePersistenceService', [
      'getWorkspacePreference',
      'saveWorkspacePreference',
      'removeWorkspacePreference',
    ]);
    mockAuthStore = jasmine.createSpyObj<AuthStore>('AuthStore', ['currentUser']);

    mockOrgApi.getUserOrganizations.and.returnValue(of(mockOrgs));
    mockWorkspaceApi.getWorkspacesForOrganization.and.returnValue(of(mockOrgWorkspaces));
    mockWorkspaceApi.getPersonalWorkspaces.and.returnValue(of(mockPersonalWorkspaces));
    mockAuthStore.currentUser.and.returnValue({ id: 'user-001' } as any);
    mockPersistence.getWorkspacePreference.and.returnValue(null);

    TestBed.configureTestingModule({
      providers: [
        WorkspaceContextStore,
        { provide: OrganizationApiService, useValue: mockOrgApi },
        { provide: WorkspaceApiService, useValue: mockWorkspaceApi },
        { provide: SubspacePersistenceService, useValue: mockPersistence },
        { provide: AuthStore, useValue: mockAuthStore },
      ],
    });

    store = TestBed.inject(WorkspaceContextStore);
  });

  it('should be created with empty initial state', () => {
    expect(store).toBeTruthy();
    expect(store.activeOrganization()).toBeNull();
    expect(store.activeWorkspace()).toBeNull();
    expect(store.availableOrganizations()).toEqual([]);
    expect(store.availableWorkspaces()).toEqual([]);
    expect(store.activeRootFolderId()).toBeNull();
    expect(store.isPersonal()).toBeTrue();
    expect(store.isOrganization()).toBeFalse();
  });

  describe('Organization Loading & Selection', () => {
    it('should load organizations and populate availableOrganizations', (done) => {
      store.loadUserOrganizations().subscribe((orgs) => {
        expect(orgs.length).toBe(2);
        expect(store.availableOrganizations().length).toBe(2);
        expect(store.availableOrganizations()[0].orgCode).toBe('ORG_1');
        expect(mockOrgApi.getUserOrganizations).toHaveBeenCalledTimes(1);
        done();
      });
    });

    it('should deduplicate organization loading if already cached within TTL', (done) => {
      store.loadUserOrganizations().subscribe(() => {
        store.loadUserOrganizations().subscribe((orgs) => {
          expect(orgs.length).toBe(2);
          expect(mockOrgApi.getUserOrganizations).toHaveBeenCalledTimes(1);
          done();
        });
      });
    });

    it('should select organization and load canonical project workspaces', (done) => {
      store.selectOrganization('ORG_1').subscribe((workspaces) => {
        expect(workspaces.length).toBe(3);
        expect(store.activeOrganization()?.orgCode).toBe('ORG_1');
        expect(store.activeOrgCode()).toBe('ORG_1');
        expect(store.isOrganization()).toBeTrue();
        expect(store.isPersonal()).toBeFalse();
        expect(store.availableWorkspaces().length).toBe(3);
        expect(store.activeWorkspace()?.id).toBe(42);
        expect(mockWorkspaceApi.getWorkspacesForOrganization).toHaveBeenCalledWith('ORG_1');
        done();
      });
    });

    it('should deduplicate workspace loading when selecting same organization again', (done) => {
      store.selectOrganization('ORG_1').subscribe(() => {
        store.selectOrganization('ORG_1').subscribe((workspaces) => {
          expect(workspaces.length).toBe(3);
          expect(mockWorkspaceApi.getWorkspacesForOrganization).toHaveBeenCalledTimes(1);
          done();
        });
      });
    });
  });

  describe('Numeric Workspace Selection in Organization Space', () => {
    beforeEach((done) => {
      store.selectOrganization('ORG_1').subscribe(() => done());
    });

    it('should select active workspace by numeric ID and update activeRootFolderId', () => {
      const success = store.selectWorkspace(42);

      expect(success).toBeTrue();
      expect(store.activeWorkspace()?.id).toBe(42);
      expect(store.activeRootFolderId()).toBe(101);
      expect(mockPersistence.saveWorkspacePreference).toHaveBeenCalledWith('user-001', 'ORG_1', 42);
    });

    it('should reject non-existent workspace ID', () => {
      const success = store.selectWorkspace(999);

      expect(success).toBeFalse();
      expect(store.error()).toContain('999 not found');
    });

    it('should reject ARCHIVED workspace', () => {
      const success = store.selectWorkspace(44);

      expect(success).toBeFalse();
      expect(store.error()).toContain('not active');
    });
  });

  describe('Default Workspace Resolution in Organization Space', () => {
    beforeEach((done) => {
      store.selectOrganization('ORG_1').subscribe(() => done());
    });

    it('should resolve default using persisted workspace preference when valid', (done) => {
      mockPersistence.getWorkspacePreference.and.returnValue(43);

      store.resolveDefaultWorkspace('ORG_1').subscribe((resolvedId) => {
        expect(resolvedId).toBe(43);
        done();
      });
    });

    it('should fall back to isDefault workspace when no preference is saved', (done) => {
      mockPersistence.getWorkspacePreference.and.returnValue(null);

      store.resolveDefaultWorkspace('ORG_1').subscribe((resolvedId) => {
        expect(resolvedId).toBe(42);
        done();
      });
    });

    it('should fall back to first active workspace if isDefault is missing without relying on name', (done) => {
      mockPersistence.getWorkspacePreference.and.returnValue(null);
      const noDefaultWorkspaces = mockOrgWorkspaces.map((w) => ({ ...w, isDefault: false }));
      mockWorkspaceApi.getWorkspacesForOrganization.and.returnValue(of(noDefaultWorkspaces));

      store.selectOrganization('ORG_1', true).subscribe(() => {
        store.resolveDefaultWorkspace('ORG_1').subscribe((resolvedId) => {
          expect(resolvedId).toBe(42);
          done();
        });
      });
    });
  });

  describe('Personal Workspace Loading & Selection', () => {
    it('should load personal workspaces via WorkspaceApiService and populate availableWorkspaces', (done) => {
      store.loadPersonalWorkspaces().subscribe((workspaces) => {
        expect(workspaces.length).toBe(3);
        expect(store.availableWorkspaces().length).toBe(3);
        expect(store.availableWorkspaces()[0].id).toBe(10);
        expect(store.availableWorkspaces()[0].organizationCode).toBeNull();
        expect(store.activeWorkspace()?.id).toBe(10);
        expect(store.activeRootFolderId()).toBe(200);
        expect(store.isPersonal()).toBeTrue();
        expect(store.isOrganization()).toBeFalse();
        expect(mockWorkspaceApi.getPersonalWorkspaces).toHaveBeenCalledTimes(1);
        done();
      });
    });

    it('should select personal workspace with numeric ID and persist preference with null orgCode', (done) => {
      store.selectPersonalWorkspace().subscribe((activeWs) => {
        expect(activeWs).not.toBeNull();
        expect(activeWs?.id).toBe(10);
        expect(activeWs?.organizationCode).toBeNull();
        expect(activeWs?.rootFolderId).toBe(200);
        expect(store.activeWorkspace()?.id).toBe(10);
        expect(store.activeRootFolderId()).toBe(200);
        expect(store.activeOrganization()).toBeNull();
        expect(mockPersistence.saveWorkspacePreference).toHaveBeenCalledWith('user-001', null, 10);

        // Now select second personal workspace #11
        const success = store.selectWorkspace(11);
        expect(success).toBeTrue();
        expect(store.activeWorkspace()?.id).toBe(11);
        expect(store.activeRootFolderId()).toBe(201);
        expect(mockPersistence.saveWorkspacePreference).toHaveBeenCalledWith('user-001', null, 11);
        done();
      });
    });

    it('should handle ZERO personal workspaces gracefully without throwing error', (done) => {
      mockWorkspaceApi.getPersonalWorkspaces.and.returnValue(of([]));

      store.selectPersonalWorkspace().subscribe((activeWs) => {
        expect(activeWs).toBeNull();
        expect(store.activeWorkspace()).toBeNull();
        expect(store.availableWorkspaces()).toEqual([]);
        expect(store.activeRootFolderId()).toBeNull();
        expect(store.activeOrganization()).toBeNull();
        expect(store.isPersonal()).toBeTrue();
        expect(store.isOrganization()).toBeFalse();
        done();
      });
    });

    it('should return null from resolveDefaultPersonalWorkspace when ZERO personal workspaces exist', (done) => {
      mockWorkspaceApi.getPersonalWorkspaces.and.returnValue(of([]));

      store.resolveDefaultPersonalWorkspace().subscribe((defaultId) => {
        expect(defaultId).toBeNull();
        done();
      });
    });

    it('should resolve default personal workspace using persisted preference over isDefault', (done) => {
      mockPersistence.getWorkspacePreference.and.returnValue(11);

      store.resolveDefaultPersonalWorkspace().subscribe((resolvedId) => {
        expect(resolvedId).toBe(11);
        done();
      });
    });

    it('should fall back to first active personal workspace if isDefault is missing', (done) => {
      mockPersistence.getWorkspacePreference.and.returnValue(null);
      const noDefaultPersonal = mockPersonalWorkspaces.map((w) => ({ ...w, isDefault: false }));
      mockWorkspaceApi.getPersonalWorkspaces.and.returnValue(of(noDefaultPersonal));

      store.loadPersonalWorkspaces(true).subscribe(() => {
        store.resolveDefaultPersonalWorkspace().subscribe((resolvedId) => {
          expect(resolvedId).toBe(10);
          done();
        });
      });
    });

    it('should reject ARCHIVED personal workspace', (done) => {
      store.loadPersonalWorkspaces().subscribe(() => {
        const success = store.selectWorkspace(12); // Workspace #12 is ARCHIVED
        expect(success).toBeFalse();
        expect(store.error()).toContain('not active');
        done();
      });
    });
  });

  describe('Personal and Organization Workspace List Isolation', () => {
    it('should strictly isolate personal workspaces and organization workspaces on context switch', (done) => {
      // 1. Select organization ORG_1
      store.selectOrganization('ORG_1').subscribe((orgWorkspaces) => {
        expect(orgWorkspaces.length).toBe(3);
        expect(store.availableWorkspaces().map((w) => w.id)).toEqual([42, 43, 44]);
        expect(store.activeOrganization()?.orgCode).toBe('ORG_1');
        expect(store.activeWorkspace()?.id).toBe(42);

        // Selecting personal workspace #10 while in ORG_1 context must FAIL
        const orgSelectPersonalSuccess = store.selectWorkspace(10);
        expect(orgSelectPersonalSuccess).toBeFalse();
        expect(store.error()).toContain('Workspace #10 not found in organization ORG_1');

        // 2. Switch to Personal Space
        store.loadPersonalWorkspaces(true).subscribe((personalWorkspaces) => {
          expect(personalWorkspaces.length).toBe(3);
          expect(store.availableWorkspaces().map((w) => w.id)).toEqual([10, 11, 12]);
          expect(store.activeOrganization()).toBeNull();
          expect(store.isPersonal()).toBeTrue();
          expect(store.activeWorkspace()?.id).toBe(10);

          // Selecting org workspace #42 while in Personal Space context must FAIL
          const personalSelectOrgSuccess = store.selectWorkspace(42);
          expect(personalSelectOrgSuccess).toBeFalse();
          expect(store.error()).toContain('Workspace #42 not found in Personal Space');
          done();
        });
      });
    });

    it('should clear/replace foreign personal workspace when switching to an organization', (done) => {
      // Start in personal space
      store.loadPersonalWorkspaces().subscribe(() => {
        expect(store.activeWorkspace()?.id).toBe(10);
        expect(store.activeOrganization()).toBeNull();

        // Switch to organization ORG_1
        store.selectOrganization('ORG_1').subscribe(() => {
          expect(store.activeOrganization()?.orgCode).toBe('ORG_1');
          expect(store.activeWorkspace()?.id).toBe(42);
          expect(store.activeWorkspace()?.organizationCode).toBe('ORG_1');
          done();
        });
      });
    });

    it('should cleanly fall back to default personal workspace when persisted preference is stale or non-existent', (done) => {
      mockPersistence.getWorkspacePreference.and.returnValue(null);

      store.loadPersonalWorkspaces(true).subscribe(() => {
        expect(store.activeWorkspace()?.id).toBe(10);
        expect(store.activeRootFolderId()).toBe(200);
        done();
      });
    });
  });

  describe('Temporary Compatibility Signals for Personal Space', () => {
    it('should provide activeSubspace() and availableSubspaces() for numeric personal workspaces', (done) => {
      store.selectPersonalWorkspace().subscribe(() => {
        const sub = store.activeSubspace();
        expect(sub).not.toBeNull();
        expect(sub?.id).toBe('10');
        expect(sub?.folderId).toBe(200);
        expect(sub?.name).toBe('My Personal Notes');
        expect(sub?.workspaceId).toBe('personal');

        const subs = store.availableSubspaces();
        expect(subs.length).toBe(3);
        expect(subs[0].id).toBe('10');
        expect(subs[1].id).toBe('11');
        expect(subs[0].workspaceId).toBe('personal');

        expect(store.activeWorkspaceId()).toBe('10');
        expect(store.activeSubspaceId()).toBe('10');
        expect(store.isPersonalWorkspace()).toBeTrue();
        done();
      });
    });
  });
});
