import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, ParamMap } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import {
  orgGuard,
  workspaceGuard,
  personalGuard,
  personalWorkspaceGuard,
  legacyRouteRedirectGuard,
} from './workspace.guard';
import { WorkspaceContextStore } from '../../state/workspace/workspace-context.store';
import { ProjectWorkspaceModel } from '../models/project-workspace.model';

describe('Workspace Guards', () => {
  let mockWorkspaceStore: jasmine.SpyObj<WorkspaceContextStore>;
  let mockRouter: jasmine.SpyObj<Router>;

  const mockOrgWorkspaces: ProjectWorkspaceModel[] = [
    {
      id: 42,
      organizationCode: 'ORG_123',
      name: 'General',
      rootFolderId: 101,
      status: 'ACTIVE',
      isDefault: true,
    },
    {
      id: 43,
      organizationCode: 'ORG_123',
      name: 'Engineering',
      rootFolderId: 102,
      status: 'ACTIVE',
      isDefault: false,
    },
    {
      id: 44,
      organizationCode: 'ORG_123',
      name: 'Archived Space',
      rootFolderId: 103,
      status: 'ARCHIVED',
      isDefault: false,
    },
  ];

  const mockPersonalWorkspaces: ProjectWorkspaceModel[] = [
    {
      id: 101,
      organizationCode: null,
      name: 'Personal Workspace',
      rootFolderId: 10,
      status: 'ACTIVE',
      isDefault: true,
    },
    {
      id: 102,
      organizationCode: null,
      name: 'Side Project',
      rootFolderId: 11,
      status: 'ACTIVE',
      isDefault: false,
    },
    {
      id: 103,
      organizationCode: null,
      name: 'Archived Personal Space',
      rootFolderId: 12,
      status: 'ARCHIVED',
      isDefault: false,
    },
  ];

  function createMockParamMap(params: Record<string, string>): ParamMap {
    return {
      get: (key: string) => params[key] || null,
      has: (key: string) => key in params,
      getAll: (key: string) => (params[key] ? [params[key]] : []),
      keys: Object.keys(params),
    };
  }

  function createMockRouteSnapshot(params: Record<string, string>, parent: ActivatedRouteSnapshot | null = null): ActivatedRouteSnapshot {
    return {
      paramMap: createMockParamMap(params),
      parent,
    } as unknown as ActivatedRouteSnapshot;
  }

  function createMockStateSnapshot(url: string): RouterStateSnapshot {
    return {
      url,
      root: {} as ActivatedRouteSnapshot,
    };
  }

  beforeEach(() => {
    mockWorkspaceStore = jasmine.createSpyObj<WorkspaceContextStore>('WorkspaceContextStore', [
      'selectOrganization',
      'selectWorkspace',
      'resolveDefaultWorkspace',
      'resolveDefaultPersonalWorkspace',
      'selectPersonalWorkspace',
      'loadPersonalWorkspaces',
      'loadUserOrganizations',
      'activeOrgCode',
      'availableWorkspaces',
      'isPersonal',
      'isOrganization',
      'enterPersonalContext',
    ]);
    mockWorkspaceStore.isOrganization.and.returnValue(false);

    mockRouter = jasmine.createSpyObj<Router>('Router', ['createUrlTree', 'navigate', 'parseUrl']);
    mockRouter.createUrlTree.and.callFake((commands: any[]) => ({ toString: () => '/' + commands.join('/') } as UrlTree));
    mockRouter.parseUrl.and.callFake((url: string) => ({
      queryParams: {},
      fragment: null,
    } as any));

    TestBed.configureTestingModule({
      providers: [
        { provide: WorkspaceContextStore, useValue: mockWorkspaceStore },
        { provide: Router, useValue: mockRouter },
      ],
    });
  });

  // ── orgGuard ───────────────────────────────────────────────────────────────
  describe('orgGuard', () => {
    it('should return true when organization selection succeeds', (done) => {
      mockWorkspaceStore.selectOrganization.and.returnValue(of(mockOrgWorkspaces));

      const route = createMockRouteSnapshot({ orgCode: 'ORG_123' });

      TestBed.runInInjectionContext(() => {
        (orgGuard(route, {} as any) as Observable<boolean | UrlTree>).subscribe((result) => {
          expect(result).toBeTrue();
          expect(mockWorkspaceStore.selectOrganization).toHaveBeenCalledWith('ORG_123');
          done();
        });
      });
    });

    it('should redirect to /w/personal/dashboard when user is not member of orgCode', (done) => {
      mockWorkspaceStore.selectOrganization.and.returnValue(throwError(() => new Error('Not found')));

      const route = createMockRouteSnapshot({ orgCode: 'UNAUTHORIZED_ORG' });

      TestBed.runInInjectionContext(() => {
        (orgGuard(route, {} as any) as Observable<boolean | UrlTree>).subscribe((result) => {
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/w', 'personal', 'dashboard']);
          expect(result).toBeDefined();
          done();
        });
      });
    });

    it('should redirect to /w/personal/dashboard when orgCode param is missing', (done) => {
      const route = createMockRouteSnapshot({});

      TestBed.runInInjectionContext(() => {
        (orgGuard(route, {} as any) as Observable<boolean | UrlTree>).subscribe(() => {
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/w', 'personal', 'dashboard']);
          done();
        });
      });
    });
  });

  // ── workspaceGuard (Organization Workspaces) ───────────────────────────────
  describe('workspaceGuard', () => {
    it('should resolve "default" workspace ID and redirect to canonical numeric route preserving feature suffix', (done) => {
      mockWorkspaceStore.resolveDefaultWorkspace.and.returnValue(of(42));

      const route = createMockRouteSnapshot({ orgCode: 'ORG_123', workspaceId: 'default' });
      const state = createMockStateSnapshot('/org/ORG_123/w/default/explorer');

      TestBed.runInInjectionContext(() => {
        (workspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe(() => {
          expect(mockWorkspaceStore.resolveDefaultWorkspace).toHaveBeenCalledWith('ORG_123');
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/org', 'ORG_123', 'w', 42, 'explorer']);
          done();
        });
      });
    });

    it('should preserve deep-link suffixes (e.g. explorer/folder/:id) when resolving default org workspace', (done) => {
      mockWorkspaceStore.resolveDefaultWorkspace.and.returnValue(of(42));

      const route = createMockRouteSnapshot({ orgCode: 'ORG_123', workspaceId: 'default' });
      const state = createMockStateSnapshot('/org/ORG_123/w/default/explorer/folder/205');

      TestBed.runInInjectionContext(() => {
        (workspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe(() => {
          expect(mockWorkspaceStore.resolveDefaultWorkspace).toHaveBeenCalledWith('ORG_123');
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith([
            '/org',
            'ORG_123',
            'w',
            42,
            'explorer',
            'folder',
            '205',
          ]);
          done();
        });
      });
    });

    it('should preserve organization zero-workspace behavior by redirecting to /w/personal/dashboard on failure', (done) => {
      // When an organization has no active workspaces, resolveDefaultWorkspace throws
      mockWorkspaceStore.resolveDefaultWorkspace.and.returnValue(
        throwError(() => new Error('Organization ORG_123 has no active workspaces'))
      );

      const route = createMockRouteSnapshot({ orgCode: 'ORG_123', workspaceId: 'default' });
      const state = createMockStateSnapshot('/org/ORG_123/w/default/dashboard');

      TestBed.runInInjectionContext(() => {
        (workspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe(() => {
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/w', 'personal', 'dashboard']);
          done();
        });
      });
    });

    it('should return true for valid numeric workspace ID', (done) => {
      mockWorkspaceStore.activeOrgCode.and.returnValue('ORG_123');
      mockWorkspaceStore.availableWorkspaces.and.returnValue(mockOrgWorkspaces);
      mockWorkspaceStore.selectWorkspace.and.returnValue(true);

      const route = createMockRouteSnapshot({ orgCode: 'ORG_123', workspaceId: '43' });
      const state = createMockStateSnapshot('/org/ORG_123/w/43/explorer');

      TestBed.runInInjectionContext(() => {
        (workspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe((result) => {
          expect(result).toBeTrue();
          expect(mockWorkspaceStore.selectWorkspace).toHaveBeenCalledWith(43);
          done();
        });
      });
    });

    it('should reject non-numeric workspace ID by redirecting to default workspace', (done) => {
      const route = createMockRouteSnapshot({ orgCode: 'ORG_123', workspaceId: 'invalid_abc' });
      const state = createMockStateSnapshot('/org/ORG_123/w/invalid_abc/dashboard');

      TestBed.runInInjectionContext(() => {
        (workspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe(() => {
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/org', 'ORG_123', 'w', 'default', 'dashboard']);
          done();
        });
      });
    });

    it('should reject archived or inaccessible workspace by redirecting to default', (done) => {
      mockWorkspaceStore.activeOrgCode.and.returnValue('ORG_123');
      mockWorkspaceStore.availableWorkspaces.and.returnValue(mockOrgWorkspaces);
      mockWorkspaceStore.selectWorkspace.and.returnValue(false);

      const route = createMockRouteSnapshot({ orgCode: 'ORG_123', workspaceId: '44' });
      const state = createMockStateSnapshot('/org/ORG_123/w/44/dashboard');

      TestBed.runInInjectionContext(() => {
        (workspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe(() => {
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/org', 'ORG_123', 'w', 'default', 'dashboard']);
          done();
        });
      });
    });

    it('should reject personal workspace ID passed to organization workspace guard', (done) => {
      mockWorkspaceStore.activeOrgCode.and.returnValue('ORG_123');
      mockWorkspaceStore.availableWorkspaces.and.returnValue(mockOrgWorkspaces);
      // Personal workspace ID is rejected by selectWorkspace in org context
      mockWorkspaceStore.selectWorkspace.and.returnValue(false);

      const route = createMockRouteSnapshot({ orgCode: 'ORG_123', workspaceId: '101' });
      const state = createMockStateSnapshot('/org/ORG_123/w/101/dashboard');

      TestBed.runInInjectionContext(() => {
        (workspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe(() => {
          expect(mockWorkspaceStore.selectWorkspace).toHaveBeenCalledWith(101);
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/org', 'ORG_123', 'w', 'default', 'dashboard']);
          done();
        });
      });
    });

    it('should read params from parent route snapshot if needed', (done) => {
      mockWorkspaceStore.activeOrgCode.and.returnValue('ORG_123');
      mockWorkspaceStore.availableWorkspaces.and.returnValue(mockOrgWorkspaces);
      mockWorkspaceStore.selectWorkspace.and.returnValue(true);

      const parentRoute = createMockRouteSnapshot({ orgCode: 'ORG_123', workspaceId: '42' });
      const childRoute = createMockRouteSnapshot({}, parentRoute);
      const state = createMockStateSnapshot('/org/ORG_123/w/42/dashboard');

      TestBed.runInInjectionContext(() => {
        (workspaceGuard(childRoute, state) as Observable<boolean | UrlTree>).subscribe((result) => {
          expect(result).toBeTrue();
          expect(mockWorkspaceStore.selectWorkspace).toHaveBeenCalledWith(42);
          done();
        });
      });
    });
  });

  // ── personalGuard (Personal Space Root) ────────────────────────────────────
  describe('personalGuard', () => {
    it('should load personal workspaces and user organizations and return true', (done) => {
      mockWorkspaceStore.loadPersonalWorkspaces.and.returnValue(of(mockPersonalWorkspaces));
      mockWorkspaceStore.loadUserOrganizations.and.returnValue(of([{ orgCode: 'ORG_1', name: 'Org One' } as any]));

      TestBed.runInInjectionContext(() => {
        (personalGuard({} as any, {} as any) as Observable<boolean>).subscribe((result) => {
          expect(result).toBeTrue();
          expect(mockWorkspaceStore.loadPersonalWorkspaces).toHaveBeenCalled();
          expect(mockWorkspaceStore.loadUserOrganizations).toHaveBeenCalled();
          done();
        });
      });
    });

    it('should return true even when user has zero personal workspaces and zero organizations', (done) => {
      mockWorkspaceStore.loadPersonalWorkspaces.and.returnValue(of([]));
      mockWorkspaceStore.loadUserOrganizations.and.returnValue(of([]));

      TestBed.runInInjectionContext(() => {
        (personalGuard({} as any, {} as any) as Observable<boolean>).subscribe((result) => {
          expect(result).toBeTrue();
          expect(mockWorkspaceStore.loadPersonalWorkspaces).toHaveBeenCalled();
          expect(mockWorkspaceStore.loadUserOrganizations).toHaveBeenCalled();
          done();
        });
      });
    });

    it('should preserve organization context and avoid loading personal workspaces when navigating to notifications from an organization', (done) => {
      mockWorkspaceStore.isOrganization.and.returnValue(true);

      const mockState = { url: '/w/personal/notifications' } as RouterStateSnapshot;

      TestBed.runInInjectionContext(() => {
        (personalGuard({} as any, mockState) as Observable<boolean>).subscribe((result) => {
          expect(result).toBeTrue();
          expect(mockWorkspaceStore.loadPersonalWorkspaces).not.toHaveBeenCalled();
          done();
        });
      });
    });

    it('should preserve organization context when navigating to shared-with-me from an organization', (done) => {
      mockWorkspaceStore.isOrganization.and.returnValue(true);

      const mockState = { url: '/w/personal/shared-with-me' } as RouterStateSnapshot;

      TestBed.runInInjectionContext(() => {
        (personalGuard({} as any, mockState) as Observable<boolean>).subscribe((result) => {
          expect(result).toBeTrue();
          expect(mockWorkspaceStore.loadPersonalWorkspaces).not.toHaveBeenCalled();
          done();
        });
      });
    });
  });

  // ── personalWorkspaceGuard (Personal Workspace Content) ────────────────────
  describe('personalWorkspaceGuard', () => {
    it('should resolve "default" personal workspace to canonical numeric route: /w/personal/w/:id/:feature', (done) => {
      mockWorkspaceStore.resolveDefaultPersonalWorkspace.and.returnValue(of(101));

      const route = createMockRouteSnapshot({ workspaceId: 'default' });
      const state = createMockStateSnapshot('/w/personal/w/default/dashboard');

      TestBed.runInInjectionContext(() => {
        (personalWorkspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe(() => {
          expect(mockWorkspaceStore.resolveDefaultPersonalWorkspace).toHaveBeenCalled();
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/w', 'personal', 'w', 101, 'dashboard']);
          done();
        });
      });
    });

    it('should preserve deep-link suffixes (e.g. explorer/folder/:id) when resolving default personal workspace', (done) => {
      mockWorkspaceStore.resolveDefaultPersonalWorkspace.and.returnValue(of(101));

      const route = createMockRouteSnapshot({ workspaceId: 'default' });
      const state = createMockStateSnapshot('/w/personal/w/default/explorer/folder/55');

      TestBed.runInInjectionContext(() => {
        (personalWorkspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe(() => {
          expect(mockWorkspaceStore.resolveDefaultPersonalWorkspace).toHaveBeenCalled();
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith([
            '/w',
            'personal',
            'w',
            101,
            'explorer',
            'folder',
            '55',
          ]);
          done();
        });
      });
    });

    it('should redirect to Personal Space Home (/w/personal/dashboard) when resolving default with ZERO workspaces', (done) => {
      // Zero personal workspaces: resolveDefaultPersonalWorkspace returns null
      mockWorkspaceStore.resolveDefaultPersonalWorkspace.and.returnValue(of(null));

      const route = createMockRouteSnapshot({ workspaceId: 'default' });
      const state = createMockStateSnapshot('/w/personal/w/default/dashboard');

      TestBed.runInInjectionContext(() => {
        (personalWorkspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe(() => {
          expect(mockWorkspaceStore.resolveDefaultPersonalWorkspace).toHaveBeenCalled();
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/w', 'personal', 'dashboard']);
          done();
        });
      });
    });

    it('should allow valid numeric personal workspace ID and return true', (done) => {
      mockWorkspaceStore.isPersonal.and.returnValue(true);
      mockWorkspaceStore.availableWorkspaces.and.returnValue(mockPersonalWorkspaces);
      mockWorkspaceStore.selectWorkspace.and.returnValue(true);

      const route = createMockRouteSnapshot({ workspaceId: '101' });
      const state = createMockStateSnapshot('/w/personal/w/101/dashboard');

      TestBed.runInInjectionContext(() => {
        (personalWorkspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe((result) => {
          expect(result).toBeTrue();
          expect(mockWorkspaceStore.selectWorkspace).toHaveBeenCalledWith(101);
          done();
        });
      });
    });

    it('should reject non-numeric personal workspace ID by redirecting to default', (done) => {
      const route = createMockRouteSnapshot({ workspaceId: 'personal' });
      const state = createMockStateSnapshot('/w/personal/w/personal/explorer');

      TestBed.runInInjectionContext(() => {
        (personalWorkspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe(() => {
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/w', 'personal', 'w', 'default', 'explorer']);
          done();
        });
      });
    });

    it('should reject organization workspace ID in personal workspace guard by redirecting to default', (done) => {
      mockWorkspaceStore.isPersonal.and.returnValue(true);
      mockWorkspaceStore.availableWorkspaces.and.returnValue(mockPersonalWorkspaces);
      // An org workspace ID (e.g. 42) will fail selectWorkspace in personal context
      mockWorkspaceStore.selectWorkspace.and.returnValue(false);

      const route = createMockRouteSnapshot({ workspaceId: '42' });
      const state = createMockStateSnapshot('/w/personal/w/42/dashboard');

      TestBed.runInInjectionContext(() => {
        (personalWorkspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe(() => {
          expect(mockWorkspaceStore.selectWorkspace).toHaveBeenCalledWith(42);
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/w', 'personal', 'w', 'default', 'dashboard']);
          done();
        });
      });
    });

    it('should reject archived personal workspace by redirecting to default', (done) => {
      mockWorkspaceStore.isPersonal.and.returnValue(true);
      mockWorkspaceStore.availableWorkspaces.and.returnValue(mockPersonalWorkspaces);
      mockWorkspaceStore.selectWorkspace.and.returnValue(false);

      const route = createMockRouteSnapshot({ workspaceId: '103' });
      const state = createMockStateSnapshot('/w/personal/w/103/explorer');

      TestBed.runInInjectionContext(() => {
        (personalWorkspaceGuard(route, state) as Observable<boolean | UrlTree>).subscribe(() => {
          expect(mockWorkspaceStore.selectWorkspace).toHaveBeenCalledWith(103);
          expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/w', 'personal', 'w', 'default', 'explorer']);
          done();
        });
      });
    });
  });

  // ── legacyRouteRedirectGuard ───────────────────────────────────────────────
  describe('legacyRouteRedirectGuard', () => {
    it('should translate legacy personal route /w/personal/s/default/dashboard to Personal Space Home: /w/personal/dashboard', () => {
      const route = createMockRouteSnapshot({ workspaceId: 'personal', subspaceId: 'default' });
      const state = createMockStateSnapshot('/w/personal/s/default/dashboard');

      TestBed.runInInjectionContext(() => {
        legacyRouteRedirectGuard(route, state);
        expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/w', 'personal', 'dashboard']);
      });
    });

    it('should translate legacy personal subspace reference to /w/personal/w/default/:feature without treating subspaceId as workspace ID', () => {
      // Legacy route with subspace ID: /w/personal/s/sub-123/explorer
      const route = createMockRouteSnapshot({ workspaceId: 'personal', subspaceId: 'sub-123' });
      const state = createMockStateSnapshot('/w/personal/s/sub-123/explorer');

      TestBed.runInInjectionContext(() => {
        legacyRouteRedirectGuard(route, state);
        expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/w', 'personal', 'w', 'default', 'explorer']);
      });
    });

    it('should translate legacy personal subspace deep link without treating subspaceId as workspace ID', () => {
      // Legacy route with deep link: /w/personal/s/sub-123/explorer/folder/77
      const route = createMockRouteSnapshot({ workspaceId: 'personal', subspaceId: 'sub-123' });
      const state = createMockStateSnapshot('/w/personal/s/sub-123/explorer/folder/77');

      TestBed.runInInjectionContext(() => {
        legacyRouteRedirectGuard(route, state);
        expect(mockRouter.createUrlTree).toHaveBeenCalledWith([
          '/w',
          'personal',
          'w',
          'default',
          'explorer',
          'folder',
          '77',
        ]);
      });
    });

    it('should translate legacy personal subspace dashboard reference to /w/personal/w/default/dashboard', () => {
      const route = createMockRouteSnapshot({ workspaceId: 'personal', subspaceId: 'sub-123' });
      const state = createMockStateSnapshot('/w/personal/s/sub-123/dashboard');

      TestBed.runInInjectionContext(() => {
        legacyRouteRedirectGuard(route, state);
        expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/w', 'personal', 'w', 'default', 'dashboard']);
      });
    });

    it('should translate legacy personal shared account route to /w/personal/:feature', () => {
      const route = createMockRouteSnapshot({ workspaceId: 'personal', subspaceId: 'default' });
      const state = createMockStateSnapshot('/w/personal/s/default/settings');

      TestBed.runInInjectionContext(() => {
        legacyRouteRedirectGuard(route, state);
        expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/w', 'personal', 'settings']);
      });
    });

    it('should translate legacy org content route to canonical /org/:orgCode/w/default/:feature', () => {
      const route = createMockRouteSnapshot({ workspaceId: 'ORG_123', subspaceId: 'sub-456' });
      const state = createMockStateSnapshot('/w/ORG_123/s/sub-456/explorer');

      TestBed.runInInjectionContext(() => {
        legacyRouteRedirectGuard(route, state);
        expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/org', 'ORG_123', 'w', 'default', 'explorer']);
      });
    });

    it('should translate legacy org admin route /w/ORG_123/s/default/teams to /org/ORG_123/teams', () => {
      const route = createMockRouteSnapshot({ workspaceId: 'ORG_123', subspaceId: 'default' });
      const state = createMockStateSnapshot('/w/ORG_123/s/default/teams');

      TestBed.runInInjectionContext(() => {
        legacyRouteRedirectGuard(route, state);
        expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/org', 'ORG_123', 'teams']);
      });
    });
  });
});
