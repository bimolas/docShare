import { TestBed } from '@angular/core/testing';
import { SubspacePersistenceService } from './subspace-persistence.service';
import { ProjectWorkspaceModel } from '../models/project-workspace.model';

describe('SubspacePersistenceService', () => {
  let service: SubspacePersistenceService;

  const mockWorkspaces: ProjectWorkspaceModel[] = [
    {
      id: 42,
      organizationCode: 'ORG_ABC',
      name: 'General',
      rootFolderId: 101,
      status: 'ACTIVE',
    },
    {
      id: 43,
      organizationCode: 'ORG_ABC',
      name: 'Engineering',
      rootFolderId: 102,
      status: 'ACTIVE',
    },
    {
      id: 44,
      organizationCode: 'ORG_ABC',
      name: 'Archived Space',
      rootFolderId: 103,
      status: 'ARCHIVED',
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SubspacePersistenceService);
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Canonical Workspace Preference', () => {
    it('should save and retrieve canonical numeric workspace preference', () => {
      service.saveWorkspacePreference('user-1', 'ORG_ABC', 42);

      const pref = service.getWorkspacePreference('user-1', 'ORG_ABC', mockWorkspaces);
      expect(pref).toBe(42);
      expect(typeof pref).toBe('number');
    });

    it('should return null when canonical preference does not exist in available workspaces and purge stale key', () => {
      service.saveWorkspacePreference('user-1', 'ORG_ABC', 999);

      const pref = service.getWorkspacePreference('user-1', 'ORG_ABC', mockWorkspaces);
      expect(pref).toBeNull();
      expect(localStorage.getItem('securedoc_active_ws_user-1_ORG_ABC')).toBeNull();
    });

    it('should return null when canonical workspace is ARCHIVED and purge stale key', () => {
      service.saveWorkspacePreference('user-1', 'ORG_ABC', 44);

      const pref = service.getWorkspacePreference('user-1', 'ORG_ABC', mockWorkspaces);
      expect(pref).toBeNull();
      expect(localStorage.getItem('securedoc_active_ws_user-1_ORG_ABC')).toBeNull();
    });

    it('should remove canonical workspace preference', () => {
      service.saveWorkspacePreference('user-1', 'ORG_ABC', 42);
      service.removeWorkspacePreference('user-1', 'ORG_ABC');

      const pref = service.getWorkspacePreference('user-1', 'ORG_ABC', mockWorkspaces);
      expect(pref).toBeNull();
    });
  });

  describe('Legacy Key Migration', () => {
    it('should translate legacy folderId preference to canonical workspaceId when matching rootFolderId', () => {
      // Set legacy key storing root folder ID 102 (Engineering workspace #43)
      localStorage.setItem('securedoc_subspace_pref_user-1_ORG_ABC', '102');

      const pref = service.getWorkspacePreference('user-1', 'ORG_ABC', mockWorkspaces);

      // Successfully resolved to workspace ID 43
      expect(pref).toBe(43);
      // Canonical key was saved
      expect(localStorage.getItem('securedoc_active_ws_user-1_ORG_ABC')).toBe('43');
      // Legacy key was purged
      expect(localStorage.getItem('securedoc_subspace_pref_user-1_ORG_ABC')).toBeNull();
    });

    it('should NOT translate arbitrary legacy folder ID if it does not match a workspace rootFolderId', () => {
      // Legacy key with unknown folder ID 999
      localStorage.setItem('securedoc_subspace_pref_user-1_ORG_ABC', '999');

      const pref = service.getWorkspacePreference('user-1', 'ORG_ABC', mockWorkspaces);

      // Rejects arbitrary folder ID
      expect(pref).toBeNull();
      // Purges stale legacy key
      expect(localStorage.getItem('securedoc_subspace_pref_user-1_ORG_ABC')).toBeNull();
    });

    it('should NOT translate legacy folder ID if matching workspace is ARCHIVED', () => {
      // Folder ID 103 belongs to archived workspace #44
      localStorage.setItem('securedoc_subspace_pref_user-1_ORG_ABC', '103');

      const pref = service.getWorkspacePreference('user-1', 'ORG_ABC', mockWorkspaces);

      expect(pref).toBeNull();
      expect(localStorage.getItem('securedoc_subspace_pref_user-1_ORG_ABC')).toBeNull();
    });
  });

  describe('Personal Workspace Preference', () => {
    const mockPersonalWorkspaces: ProjectWorkspaceModel[] = [
      {
        id: 10,
        organizationCode: null,
        name: 'My Personal Notes',
        rootFolderId: 200,
        status: 'ACTIVE',
      },
      {
        id: 11,
        organizationCode: null,
        name: 'Side Project',
        rootFolderId: 201,
        status: 'ACTIVE',
      },
      {
        id: 12,
        organizationCode: null,
        name: 'Archived Personal',
        rootFolderId: 202,
        status: 'ARCHIVED',
      },
    ];

    it('should save and retrieve personal workspace preference when orgCode is null', () => {
      service.saveWorkspacePreference('user-1', null, 10);

      expect(localStorage.getItem('securedoc_active_ws_user-1_personal')).toBe('10');

      const pref = service.getWorkspacePreference('user-1', null, mockPersonalWorkspaces);
      expect(pref).toBe(10);
      expect(typeof pref).toBe('number');
    });

    it('should translate legacy personal folderId preference when matching rootFolderId', () => {
      localStorage.setItem('securedoc_subspace_pref_user-1_personal', '201');

      const pref = service.getWorkspacePreference('user-1', null, mockPersonalWorkspaces);
      expect(pref).toBe(11);
      expect(localStorage.getItem('securedoc_active_ws_user-1_personal')).toBe('11');
      expect(localStorage.getItem('securedoc_subspace_pref_user-1_personal')).toBeNull();
    });

    it('should reject unresolvable legacy personal folder ID', () => {
      localStorage.setItem('securedoc_subspace_pref_user-1_personal', '999');

      const pref = service.getWorkspacePreference('user-1', null, mockPersonalWorkspaces);
      expect(pref).toBeNull();
      expect(localStorage.getItem('securedoc_subspace_pref_user-1_personal')).toBeNull();
    });

    it('should strictly isolate personal preferences from organization preferences', () => {
      service.saveWorkspacePreference('user-1', null, 10);
      service.saveWorkspacePreference('user-1', 'ORG_ABC', 42);

      expect(service.getWorkspacePreference('user-1', null, mockPersonalWorkspaces)).toBe(10);
      expect(service.getWorkspacePreference('user-1', 'ORG_ABC', mockWorkspaces)).toBe(42);

      service.removeWorkspacePreference('user-1', null);
      expect(service.getWorkspacePreference('user-1', null, mockPersonalWorkspaces)).toBeNull();
      expect(service.getWorkspacePreference('user-1', 'ORG_ABC', mockWorkspaces)).toBe(42);
    });
  });

  describe('Legacy Compatibility API', () => {
    it('should support legacy get/save/remove methods', () => {
      service.save('user-1', 'personal', 'sub-100');
      expect(service.get('user-1', 'personal')).toBe('sub-100');

      service.remove('user-1', 'personal');
      expect(service.get('user-1', 'personal')).toBeNull();
    });
  });
});
