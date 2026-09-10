import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router, NavigationEnd, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { SidebarComponent } from './sidebar.component';
import { WorkspaceContextStore } from '../../state/workspace/workspace-context.store';
import { AuthStore } from '../../state/auth/auth.store';
import { LayoutStore, ModalType } from '../../state/layout/layout.store';
import { OrganizationApiService } from '../../core/services/organization-api.service';
import { AccessRequestApiService } from '../../core/services/access-request-api.service';
import { OrganizationModel } from '../../core/models/organization.model';
import { ProjectWorkspaceModel } from '../../core/models/project-workspace.model';
import { UserProfile } from '../../core/models/user.model';
import { NotificationStore } from '../../features/notifications/state/notification.store';

describe('SidebarComponent', () => {
  let component: SidebarComponent;
  let fixture: ComponentFixture<SidebarComponent>;
  let router: Router;
  let httpTestingController: HttpTestingController;

  // Mock State Signals
  let mockActiveOrg: ReturnType<typeof signal<OrganizationModel | null>>;
  let mockActiveWs: ReturnType<typeof signal<ProjectWorkspaceModel | null>>;
  let mockAvailableOrgs: ReturnType<typeof signal<OrganizationModel[]>>;
  let mockAvailableWorkspaces: ReturnType<typeof signal<ProjectWorkspaceModel[]>>;
  let mockIsWorkspaceLoading: ReturnType<typeof signal<boolean>>;
  let resolveDefaultWorkspaceSpy: jasmine.Spy;

  let mockCurrentUser: ReturnType<typeof signal<UserProfile | null>>;
  let mockUserInitials: ReturnType<typeof signal<string>>;
  let logoutSpy: jasmine.Spy;

  let mockIsSidebarCollapsed: ReturnType<typeof signal<boolean>>;
  let mockIsMobileDrawerOpen: ReturnType<typeof signal<boolean>>;
  let openModalSpy: jasmine.Spy;
  let toggleSidebarSpy: jasmine.Spy;
  let closeMobileDrawerSpy: jasmine.Spy;

  let mockPendingInvitationsCount: ReturnType<typeof signal<number>>;
  let refreshPendingCountSpy: jasmine.Spy;
  let mockUnreadNotificationsCount: ReturnType<typeof signal<number>>;

  const mockOrgs: OrganizationModel[] = [
    {
      name: 'Acme Corporation',
      orgCode: 'ACME',
    },
    {
      name: 'Beta Industries',
      orgCode: 'BETA',
    },
  ];

  const mockWorkspaces: ProjectWorkspaceModel[] = [
    {
      id: 42,
      organizationCode: 'ACME',
      name: 'General',
      description: 'Default workspace',
      rootFolderId: 101,
      status: 'ACTIVE',
      isDefault: true,
    },
    {
      id: 43,
      organizationCode: 'ACME',
      name: 'Engineering',
      description: 'Engineering workspace',
      rootFolderId: 102,
      status: 'ACTIVE',
      isDefault: false,
    },
    {
      id: 44,
      organizationCode: 'ACME',
      name: 'Archived Team',
      description: 'Inactive workspace',
      rootFolderId: 103,
      status: 'ARCHIVED',
      isDefault: false,
    },
  ];

  beforeEach(async () => {
    mockActiveOrg = signal<OrganizationModel | null>(mockOrgs[0]);
    mockActiveWs = signal<ProjectWorkspaceModel | null>(mockWorkspaces[0]);
    mockAvailableOrgs = signal<OrganizationModel[]>(mockOrgs);
    mockAvailableWorkspaces = signal<ProjectWorkspaceModel[]>(mockWorkspaces);
    mockIsWorkspaceLoading = signal<boolean>(false);
    resolveDefaultWorkspaceSpy = jasmine.createSpy('resolveDefaultWorkspace').and.returnValue(of(42));

    const mockWorkspaceStore = {
      activeOrganization: mockActiveOrg,
      activeWorkspace: mockActiveWs,
      availableOrganizations: mockAvailableOrgs,
      availableWorkspaces: mockAvailableWorkspaces,
      isLoading: mockIsWorkspaceLoading,
      resolveDefaultWorkspace: resolveDefaultWorkspaceSpy,
      lastKnownWorkspaceId: signal<number | null>(null),
      enterPersonalContext: jasmine.createSpy('enterPersonalContext').and.returnValue(of([])),
    };

    mockCurrentUser = signal<UserProfile | null>({
      id: 'usr-1',
      email: 'jane@acme.com',
      fullName: 'Jane Doe',
      firstName: 'Jane',
      lastName: 'Doe',
      role: 'ROLE_USER',
      createdAt: '2026-01-01T00:00:00Z',
    });
    mockUserInitials = signal<string>('JD');
    logoutSpy = jasmine.createSpy('logout');

    const mockAuthStore = {
      currentUser: mockCurrentUser,
      userInitials: mockUserInitials,
      isAuthenticated: signal(true),
      logout: logoutSpy,
    };

    mockIsSidebarCollapsed = signal<boolean>(false);
    mockIsMobileDrawerOpen = signal<boolean>(false);
    openModalSpy = jasmine.createSpy('openModal');
    toggleSidebarSpy = jasmine.createSpy('toggleSidebar');
    closeMobileDrawerSpy = jasmine.createSpy('closeMobileDrawer');

    const mockLayoutStore = {
      isSidebarCollapsed: mockIsSidebarCollapsed,
      isMobileDrawerOpen: mockIsMobileDrawerOpen,
      openModal: openModalSpy,
      toggleSidebar: toggleSidebarSpy,
      closeMobileDrawer: closeMobileDrawerSpy,
    };

    mockPendingInvitationsCount = signal<number>(2);
    refreshPendingCountSpy = jasmine.createSpy('refreshPendingCount');

    const mockOrgApiService = {
      pendingInvitationsCount: mockPendingInvitationsCount,
      refreshPendingCount: refreshPendingCountSpy,
    };

    const mockAccessRequestApiService = {
      getPendingReviews: jasmine.createSpy('getPendingReviews').and.returnValue(of({ content: [], totalElements: 0 })),
    };

    mockUnreadNotificationsCount = signal<number>(5);
    const refreshUnreadCountSpy = jasmine.createSpy('refreshUnreadCount');

    const mockNotificationStore = {
      unreadCount: mockUnreadNotificationsCount,
      refreshUnreadCount: refreshUnreadCountSpy,
    };

    await TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: WorkspaceContextStore, useValue: mockWorkspaceStore },
        { provide: AuthStore, useValue: mockAuthStore },
        { provide: LayoutStore, useValue: mockLayoutStore },
        { provide: OrganizationApiService, useValue: mockOrgApiService },
        { provide: AccessRequestApiService, useValue: mockAccessRequestApiService },
        { provide: NotificationStore, useValue: mockNotificationStore },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SidebarComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    httpTestingController = TestBed.inject(HttpTestingController);

    spyOn(router, 'navigate');
    fixture.detectChanges();
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should create SidebarComponent', () => {
    expect(component).toBeTruthy();
    expect(refreshPendingCountSpy).toHaveBeenCalled();
  });

  // ── Constraint 7 & 8: Subspace elimination and zero calls to /subspaces ──
  describe('Zero /subspaces calls & Legacy De-coupling (Constraints 7 & 8)', () => {
    it('should perform ZERO calls to /subspaces and invoke no deprecated subspace signals', () => {
      // 1. Verify component does NOT have or call legacy subspace methods
      expect((component as any).getOrganizationSubspaces).toBeUndefined();
      expect((component as any).activeSubspace).toBeUndefined();
      expect((component as any).availableSubspaces).toBeUndefined();
      expect((component as any).activeWorkspaceId).toBeUndefined();
      expect((component as any).activeSubspaceId).toBeUndefined();

      // 2. Verify http backend recorded zero calls to /subspaces
      httpTestingController.expectNone((req) => req.url.includes('/subspaces'));

      // 3. Verify rendered UI has zero user-facing "Subspace" occurrences
      const text = fixture.nativeElement.textContent || '';
      expect(text).not.toContain('Subspace');
      expect(text).not.toContain('subspaces');
    });
  });

  // ── Level 1: Organization & Context Selector ──
  describe('Level 1: Organization & Context Selector', () => {
    it('should display Organization name and subtitle in organization context', () => {
      const el: HTMLElement = fixture.nativeElement;
      const title = el.querySelector('.workspace-level .node-title');
      const sub = el.querySelector('.workspace-level .node-sub');

      expect(title?.textContent?.trim()).toBe('Acme Corporation');
      expect(sub?.textContent?.trim()).toBe('Organization');
    });

    it('should display "Personal Space" and subtitle in personal context', () => {
      mockActiveOrg.set(null);
      mockActiveWs.set(null);
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      const title = el.querySelector('.workspace-level .node-title');
      const sub = el.querySelector('.workspace-level .node-sub');

      expect(title?.textContent?.trim()).toBe('Personal Space');
      expect(sub?.textContent?.trim()).toBe('Personal');
    });

    it('should toggle organization popover on click', () => {
      expect(component.activePopover()).toBeNull();

      const button = fixture.nativeElement.querySelector('.workspace-node');
      button.click();
      fixture.detectChanges();

      expect(component.activePopover()).toBe('organization');
      const popover = fixture.nativeElement.querySelector('.popover-card');
      expect(popover).toBeTruthy();
      expect(popover.textContent).toContain('ORGANIZATIONS & CONTEXT');

      button.click();
      fixture.detectChanges();
      expect(component.activePopover()).toBeNull();
    });

    it('should navigate to /w/personal/:feature when Personal Space is selected', () => {
      spyOnProperty(router, 'url', 'get').and.returnValue('/org/ACME/w/42/explorer');
      component.activePopover.set('organization');
      fixture.detectChanges();

      component.onSelectPersonal();

      expect(router.navigate).toHaveBeenCalledWith(['/w', 'personal', 'explorer']);
      expect(component.activePopover()).toBeNull();
      expect(closeMobileDrawerSpy).toHaveBeenCalled();
    });

    it('should fallback to dashboard when switching to Personal Space from an org-only route', () => {
      spyOnProperty(router, 'url', 'get').and.returnValue('/org/ACME/teams');

      component.onSelectPersonal();

      expect(router.navigate).toHaveBeenCalledWith(['/w', 'personal', 'dashboard']);
    });

    it('should resolve default workspace via store when an organization is selected', () => {
      spyOnProperty(router, 'url', 'get').and.returnValue('/org/ACME/w/42/explorer');
      resolveDefaultWorkspaceSpy.and.returnValue(of(99));

      component.onSelectOrganization('BETA');

      expect(resolveDefaultWorkspaceSpy).toHaveBeenCalledWith('BETA');
      expect(router.navigate).toHaveBeenCalledWith(['/org', 'BETA', 'w', '99', 'explorer']);
      expect(component.activePopover()).toBeNull();
    });

    it('should fallback to route resolver /org/:orgCode/w/default/:feature only if default resolution fails', () => {
      spyOnProperty(router, 'url', 'get').and.returnValue('/org/ACME/w/42/dashboard');
      resolveDefaultWorkspaceSpy.and.returnValue(throwError(() => new Error('Resolution failed')));

      component.onSelectOrganization('BETA');

      expect(router.navigate).toHaveBeenCalledWith(['/org', 'BETA', 'w', 'default', 'dashboard']);
    });

    it('should navigate directly to org-level features without workspace resolution when selecting an org', () => {
      spyOnProperty(router, 'url', 'get').and.returnValue('/org/ACME/members');

      component.onSelectOrganization('BETA');

      expect(resolveDefaultWorkspaceSpy).not.toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['/org', 'BETA', 'members']);
    });

    it('should open CREATE_ORGANIZATION modal when clicking Create organization', () => {
      component.onCreateOrganization();

      expect(openModalSpy).toHaveBeenCalledWith('CREATE_ORGANIZATION');
      expect(component.activePopover()).toBeNull();
    });
  });

  // ── Level 2: Project Workspace Selector (Personal & Organization Contexts) ──
  describe('Level 2: Project Workspace Selector', () => {
    const mockPersonalWorkspaces: ProjectWorkspaceModel[] = [
      {
        id: 101,
        organizationCode: null,
        name: 'Personal Alpha',
        description: null,
        rootFolderId: 201,
        status: 'ACTIVE',
        isDefault: true,
      },
      {
        id: 102,
        organizationCode: null,
        name: 'Personal Beta',
        description: null,
        rootFolderId: 202,
        status: 'ACTIVE',
        isDefault: false,
      },
    ];

    describe('Organization Context Workspaces', () => {
      beforeEach(() => {
        mockActiveOrg.set(mockOrgs[0]);
        mockActiveWs.set(mockWorkspaces[0]);
        mockAvailableWorkspaces.set(mockWorkspaces);
        fixture.detectChanges();
      });

      it('should render Level 2 workspace selector with workspace name and subtitle', () => {
        const el: HTMLElement = fixture.nativeElement;
        const subspaceLevel = el.querySelector('.subspace-level');
        expect(subspaceLevel).toBeTruthy();

        const title = el.querySelector('.subspace-level .node-title');
        const sub = el.querySelector('.subspace-level .node-sub');
        expect(title?.textContent?.trim()).toBe('General');
        expect(sub?.textContent?.trim()).toBe('Workspace');
      });

      it('should open WORKSPACES popover on click and list organization workspaces with numeric IDs', () => {
        const button = fixture.nativeElement.querySelector('.subspace-node');
        button.click();
        fixture.detectChanges();

        expect(component.activePopover()).toBe('workspace');
        const header = fixture.nativeElement.querySelector('.popover-header');
        expect(header?.textContent?.trim()).toBe('WORKSPACES');

        const items = fixture.nativeElement.querySelectorAll('.popover-item');
        expect(items.length).toBe(3);
      });

      it('should navigate to numeric org workspace route when an active workspace is clicked', () => {
        spyOnProperty(router, 'url', 'get').and.returnValue('/org/ACME/w/42/explorer');
        const targetWs = mockWorkspaces[1]; // Engineering, id: 43

        component.onSelectWorkspace(targetWs);

        expect(router.navigate).toHaveBeenCalledWith(['/org', 'ACME', 'w', '43', 'explorer']);
        expect(component.activePopover()).toBeNull();
        expect(closeMobileDrawerSpy).toHaveBeenCalled();
      });

      it('should NOT navigate if selected workspace is ARCHIVED and mark button disabled', () => {
        const archivedWs = mockWorkspaces[2]; // Archived Team, id: 44, status: 'ARCHIVED'

        component.onSelectWorkspace(archivedWs);

        expect(router.navigate).not.toHaveBeenCalled();

        component.activePopover.set('workspace');
        fixture.detectChanges();
        const buttons: HTMLButtonElement[] = Array.from(
          fixture.nativeElement.querySelectorAll('.popover-item')
        );
        const archivedBtn = buttons.find((b) => b.textContent?.includes('Archived Team'));
        expect(archivedBtn?.disabled).toBeTrue();
        expect(archivedBtn?.classList.contains('archived-item')).toBeTrue();
      });
    });

    describe('Personal Context Workspaces', () => {
      it('should render Level 2 with "No workspace" and empty state when personal space has zero workspaces', () => {
        mockActiveOrg.set(null);
        mockActiveWs.set(null);
        mockAvailableWorkspaces.set([]);
        fixture.detectChanges();

        const el: HTMLElement = fixture.nativeElement;
        const subspaceLevel = el.querySelector('.subspace-level');
        expect(subspaceLevel).toBeTruthy();

        const title = el.querySelector('.subspace-level .node-title');
        expect(title?.textContent?.trim()).toBe('No workspace');

        // Open popover
        const button = fixture.nativeElement.querySelector('.subspace-node');
        button.click();
        fixture.detectChanges();

        expect(component.activePopover()).toBe('workspace');
        const header = fixture.nativeElement.querySelector('.popover-header');
        expect(header?.textContent?.trim()).toBe('PERSONAL WORKSPACES');

        const emptyState = fixture.nativeElement.querySelector('.popover-empty-state');
        expect(emptyState).toBeTruthy();
        expect(emptyState?.textContent).toContain('No workspaces yet');

        // "Create workspace" action must still be available
        const actionBtn = fixture.nativeElement.querySelector('.popover-action-btn');
        expect(actionBtn).toBeTruthy();
        expect(actionBtn?.textContent).toContain('Create workspace');
      });

      it('should display "Loading..." and loading state in popover when workspaces are loading', () => {
        mockActiveOrg.set(null);
        mockActiveWs.set(null);
        mockAvailableWorkspaces.set([]);
        mockIsWorkspaceLoading.set(true);
        fixture.detectChanges();

        const title = fixture.nativeElement.querySelector('.subspace-level .node-title');
        expect(title?.textContent?.trim()).toBe('Loading...');

        // Open popover
        const button = fixture.nativeElement.querySelector('.subspace-node');
        button.click();
        fixture.detectChanges();

        const emptyState = fixture.nativeElement.querySelector('.popover-empty-state');
        expect(emptyState?.textContent).toContain('Loading workspaces...');
      });

      it('should render active workspace name when one personal workspace exists', () => {
        mockActiveOrg.set(null);
        mockActiveWs.set(mockPersonalWorkspaces[0]);
        mockAvailableWorkspaces.set([mockPersonalWorkspaces[0]]);
        fixture.detectChanges();

        const title = fixture.nativeElement.querySelector('.subspace-level .node-title');
        expect(title?.textContent?.trim()).toBe('Personal Alpha');

        const button = fixture.nativeElement.querySelector('.subspace-node');
        button.click();
        fixture.detectChanges();

        const items = fixture.nativeElement.querySelectorAll('.popover-item');
        expect(items.length).toBe(1);
        expect(items[0]?.textContent).toContain('Personal Alpha');
      });

      it('should list all personal workspaces and navigate to personal workspace route on selection', () => {
        mockActiveOrg.set(null);
        mockActiveWs.set(mockPersonalWorkspaces[0]);
        mockAvailableWorkspaces.set(mockPersonalWorkspaces);
        spyOnProperty(router, 'url', 'get').and.returnValue('/w/personal/w/101/explorer');
        fixture.detectChanges();

        const button = fixture.nativeElement.querySelector('.subspace-node');
        button.click();
        fixture.detectChanges();

        const items = fixture.nativeElement.querySelectorAll('.popover-item');
        expect(items.length).toBe(2);

        // Select second personal workspace (Personal Beta, id 102)
        component.onSelectWorkspace(mockPersonalWorkspaces[1]);

        expect(router.navigate).toHaveBeenCalledWith(['/w', 'personal', 'w', '102', 'explorer']);
        expect(component.activePopover()).toBeNull();
      });

      it('should strictly isolate personal workspaces from organization workspaces', () => {
        // In personal context:
        mockActiveOrg.set(null);
        mockActiveWs.set(null);
        mockAvailableWorkspaces.set(mockPersonalWorkspaces);
        fixture.detectChanges();

        component.activePopover.set('workspace');
        fixture.detectChanges();

        const text = fixture.nativeElement.querySelector('.popover-list')?.textContent || '';
        expect(text).toContain('Personal Alpha');
        expect(text).not.toContain('General');
        expect(text).not.toContain('Engineering');
      });
    });

    describe('Create Workspace Action', () => {
      it('should open canonical CREATE_WORKSPACE modal when clicking Create workspace', () => {
        component.onCreateWorkspace();

        expect(openModalSpy).toHaveBeenCalledWith('CREATE_WORKSPACE');
        expect(component.activePopover()).toBeNull();
      });
    });
  });

  // ── Navigation Route Construction (Constraint 3) ──
  describe('getNavItemRoute (Constraint 3: No "default" fallback in normal navigation)', () => {
    it('should construct /org/:orgCode/w/:numericId/:feature for workspace content features in org context', () => {
      expect(component.getNavItemRoute('dashboard')).toEqual(['/org', 'ACME', 'w', '42', 'dashboard']);
      expect(component.getNavItemRoute('explorer')).toEqual(['/org', 'ACME', 'w', '42', 'explorer']);
      expect(component.getNavItemRoute('permissions')).toEqual(['/org', 'ACME', 'w', '42', 'permissions']);
    });

    it('should route shared discovery views to personal-level routes even in org context', () => {
      expect(component.getNavItemRoute('shared-with-me')).toEqual(['/w', 'personal', 'shared-with-me']);
      expect(component.getNavItemRoute('shared-by-me')).toEqual(['/w', 'personal', 'shared-by-me']);
    });

    it('should NEVER contain "default" in getNavItemRoute() for normal navigation', () => {
      const route = component.getNavItemRoute('dashboard');
      expect(route).not.toContain('default');
    });

    it('should construct /org/:orgCode/:feature for organization-level admin features', () => {
      expect(component.getNavItemRoute('members')).toEqual(['/org', 'ACME', 'members']);
      expect(component.getNavItemRoute('teams')).toEqual(['/org', 'ACME', 'teams']);
    });

    it('should construct /org/:orgCode/:feature for shared account features in org context', () => {
      expect(component.getNavItemRoute('audit')).toEqual(['/org', 'ACME', 'audit']);
      expect(component.getNavItemRoute('invitations')).toEqual(['/org', 'ACME', 'invitations']);
      expect(component.getNavItemRoute('settings')).toEqual(['/org', 'ACME', 'settings']);
      expect(component.getNavItemRoute('profile')).toEqual(['/org', 'ACME', 'profile']);
    });

    it('should construct /w/personal/:feature for routes in personal context', () => {
      mockActiveOrg.set(null);
      mockActiveWs.set(null);
      fixture.detectChanges();

      expect(component.getNavItemRoute('dashboard')).toEqual(['/w', 'personal', 'dashboard']);
      expect(component.getNavItemRoute('explorer')).toEqual(['/w', 'personal', 'explorer']);
      expect(component.getNavItemRoute('settings')).toEqual(['/w', 'personal', 'settings']);
      expect(component.getNavItemRoute('audit')).toEqual(['/w', 'personal', 'audit']);
    });
  });

  // ── Badges and Interactions ──
  describe('Badges and Interactions', () => {
    it('should display invitations badge count from OrganizationApiService', () => {
      const count = component.getItemBadgeCount({ label: 'Invitations', icon: 'mail', route: 'invitations' });
      expect(count).toBe(2);

      const countOther = component.getItemBadgeCount({ label: 'Explorer', icon: 'folder', route: 'explorer' });
      expect(countOther).toBe(0);
    });

    it('should display real unread notifications badge count from NotificationStore', () => {
      const count = component.getItemBadgeCount({ label: 'Notifications', icon: 'bell', route: 'notifications' });
      expect(count).toBe(5);

      mockUnreadNotificationsCount.set(0);
      expect(component.getItemBadgeCount({ label: 'Notifications', icon: 'bell', route: 'notifications' })).toBe(0);
    });

    it('should include Notifications under the MANAGEMENT group', () => {
      const managementGroup = component.visibleNavGroups().find((g) => g.title === 'MANAGEMENT');
      expect(managementGroup).toBeDefined();
      const notificationItem = managementGroup?.items.find((item) => item.route === 'notifications');
      expect(notificationItem).toBeDefined();
      expect(notificationItem?.label).toBe('Notifications');
      expect(notificationItem?.icon).toBe('bell');
    });

    it('should construct canonical /w/personal/notifications route in both personal and org contexts', () => {
      // In Org context
      expect(component.getNavItemRoute('notifications')).toEqual(['/w', 'personal', 'notifications']);

      // In Personal context
      mockActiveOrg.set(null);
      mockActiveWs.set(null);
      fixture.detectChanges();
      expect(component.getNavItemRoute('notifications')).toEqual(['/w', 'personal', 'notifications']);
    });

    it('should close popovers on Escape key', () => {
      component.activePopover.set('organization');
      component.onEscapeKey();
      expect(component.activePopover()).toBeNull();
    });

    it('should toggle sidebar collapse state', () => {
      component.toggleCollapse();
      expect(toggleSidebarSpy).toHaveBeenCalled();
    });

    it('should call authStore.logout() on sign out', () => {
      component.onLogout();
      expect(logoutSpy).toHaveBeenCalled();
      expect(component.activePopover()).toBeNull();
    });
  });
});
