import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { TopNavComponent } from './top-nav.component';
import { AuthStore } from '../../state/auth/auth.store';
import { WorkspaceContextStore } from '../../state/workspace/workspace-context.store';
import { NotificationStore } from '../../features/notifications/state/notification.store';
import { OrganizationModel } from '../../core/models/organization.model';
import { ProjectWorkspaceModel } from '../../core/models/project-workspace.model';
import { UserProfile } from '../../core/models/user.model';

describe('TopNavComponent', () => {
  let component: TopNavComponent;
  let fixture: ComponentFixture<TopNavComponent>;

  let mockCurrentUser: ReturnType<typeof signal<UserProfile | null>>;
  let mockUserInitials: ReturnType<typeof signal<string>>;
  let mockActiveOrg: ReturnType<typeof signal<OrganizationModel | null>>;
  let mockActiveWs: ReturnType<typeof signal<ProjectWorkspaceModel | null>>;
  let mockUnreadCount: ReturnType<typeof signal<number>>;
  let mockIsPopoverOpen: ReturnType<typeof signal<boolean>>;
  let togglePopoverSpy: jasmine.Spy;
  let closePopoverSpy: jasmine.Spy;
  let refreshUnreadCountSpy: jasmine.Spy;
  let logoutSpy: jasmine.Spy;

  beforeEach(async () => {
    mockCurrentUser = signal<UserProfile | null>({
      id: 'user-1',
      email: 'alex@example.com',
      fullName: 'Alex Vance',
      firstName: 'Alex',
      lastName: 'Vance',
      role: 'ROLE_USER',
      createdAt: '2026-01-01T00:00:00Z',
    });
    mockUserInitials = signal<string>('AV');
    mockActiveOrg = signal<OrganizationModel | null>(null);
    mockActiveWs = signal<ProjectWorkspaceModel | null>(null);
    mockUnreadCount = signal<number>(0);
    mockIsPopoverOpen = signal<boolean>(false);
    togglePopoverSpy = jasmine.createSpy('togglePopover');
    closePopoverSpy = jasmine.createSpy('closePopover');
    refreshUnreadCountSpy = jasmine.createSpy('refreshUnreadCount');
    logoutSpy = jasmine.createSpy('logout');

    const mockAuthStore = {
      currentUser: mockCurrentUser,
      userInitials: mockUserInitials,
      logout: logoutSpy,
    };

    const mockWorkspaceStore = {
      activeOrganization: mockActiveOrg,
      activeWorkspace: mockActiveWs,
    };

    const mockNotificationStore = {
      notifications: signal([]),
      actionRequiredNotifications: signal([]),
      invitationNotifications: signal([]),
      unreadCount: mockUnreadCount,
      isPopoverOpen: mockIsPopoverOpen,
      isLoading: signal(false),
      error: signal(null),
      hasNotifications: () => false,
      togglePopover: togglePopoverSpy,
      closePopover: closePopoverSpy,
      refreshUnreadCount: refreshUnreadCountSpy,
      markAllAsRead: jasmine.createSpy('markAllAsRead'),
      markAsRead: jasmine.createSpy('markAsRead'),
      markAsUnread: jasmine.createSpy('markAsUnread'),
      loadNotifications: jasmine.createSpy('loadNotifications'),
    };

    await TestBed.configureTestingModule({
      imports: [TopNavComponent],
      providers: [
        { provide: AuthStore, useValue: mockAuthStore },
        { provide: WorkspaceContextStore, useValue: mockWorkspaceStore },
        { provide: NotificationStore, useValue: mockNotificationStore },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TopNavComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create TopNavComponent', () => {
    expect(component).toBeTruthy();
  });

  describe('Personal Context Navigation', () => {
    it('should render "Personal Space" (Personal Home / Zero Workspace) with no secondary workspace breadcrumb when activeWorkspace is null', () => {
      mockActiveOrg.set(null);
      mockActiveWs.set(null);
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      const personalItem = el.querySelector('.personal-item');
      expect(personalItem).toBeTruthy();
      expect(personalItem?.textContent).toContain('Personal Space');

      const workspaceItem = el.querySelector('.workspace-item');
      expect(workspaceItem).toBeNull();

      const chevron = el.querySelector('.breadcrumb-arrow');
      expect(chevron).toBeNull();
    });

    it('should render "Personal Space > Workspace Name" when activeWorkspace is present in personal context', () => {
      mockActiveOrg.set(null);
      mockActiveWs.set({
        id: 77,
        organizationCode: null,
        name: 'Personal Projects',
        rootFolderId: 200,
        status: 'ACTIVE',
        isDefault: true,
      });
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      const personalItem = el.querySelector('.personal-item');
      expect(personalItem).toBeTruthy();
      expect(personalItem?.textContent).toContain('Personal Space');

      const workspaceItem = el.querySelector('.workspace-item');
      expect(workspaceItem).toBeTruthy();
      expect(workspaceItem?.textContent).toContain('Personal Projects');

      const chevron = el.querySelector('.breadcrumb-arrow');
      expect(chevron).toBeTruthy();
    });

    it('should contain ZERO occurrences of "Subspace" in the rendered markup', () => {
      mockActiveOrg.set(null);
      mockActiveWs.set(null);
      fixture.detectChanges();

      const text = fixture.nativeElement.textContent || '';
      expect(text.toLowerCase()).not.toContain('subspace');
    });
  });

  describe('Organization Context Navigation', () => {
    beforeEach(() => {
      mockActiveOrg.set({
        name: 'Acme Corporation',
        orgCode: 'ACME',
      });
      mockActiveWs.set({
        id: 42,
        organizationCode: 'ACME',
        name: 'Engineering',
        rootFolderId: 101,
        status: 'ACTIVE',
        isDefault: false,
      });
      fixture.detectChanges();
    });

    it('should render organization name and active workspace name breadcrumbs', () => {
      const el: HTMLElement = fixture.nativeElement;

      const orgItem = el.querySelector('.organization-item');
      expect(orgItem).toBeTruthy();
      expect(orgItem?.textContent).toContain('Acme Corporation');

      const wsItem = el.querySelector('.workspace-item');
      expect(wsItem).toBeTruthy();
      expect(wsItem?.textContent).toContain('Engineering');

      const arrow = el.querySelector('.breadcrumb-arrow');
      expect(arrow).toBeTruthy();
    });

    it('should contain ZERO occurrences of "Subspace" in the rendered markup', () => {
      const text = fixture.nativeElement.textContent || '';
      expect(text.toLowerCase()).not.toContain('subspace');
    });

    it('should omit workspace breadcrumb when activeWorkspace is null in org context', () => {
      mockActiveWs.set(null);
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      const orgItem = el.querySelector('.organization-item');
      expect(orgItem).toBeTruthy();
      expect(orgItem?.textContent).toContain('Acme Corporation');

      const wsItem = el.querySelector('.workspace-item');
      expect(wsItem).toBeNull();
    });
  });

  describe('Header Actions & User Profile', () => {
    it('should emit toggleSidebar when sidebar toggle button is clicked', () => {
      let emitted = false;
      component.toggleSidebar.subscribe(() => {
        emitted = true;
      });

      const button = fixture.nativeElement.querySelector('.menu-toggle-btn');
      button.click();

      expect(emitted).toBeTrue();
    });

    it('should display user initials and first name', () => {
      const el: HTMLElement = fixture.nativeElement;
      const avatar = el.querySelector('.user-avatar');
      const name = el.querySelector('.user-name');

      expect(avatar?.textContent?.trim()).toBe('AV');
      expect(name?.textContent?.trim()).toBe('Alex');
    });

    it('should call authStore.logout() when sign out button is clicked', () => {
      const logoutBtn = fixture.nativeElement.querySelector('.logout-action-btn');
      logoutBtn.click();

      expect(logoutSpy).toHaveBeenCalled();
    });

    it('should display notification badge when unreadCount is greater than 0', () => {
      mockUnreadCount.set(3);
      fixture.detectChanges();

      const badge = fixture.nativeElement.querySelector('.notification-badge');
      expect(badge).toBeTruthy();
      expect(badge?.textContent?.trim()).toBe('3');
    });

    it('should toggle notification popover when bell button is clicked', () => {
      const bellBtn: HTMLButtonElement | null = fixture.nativeElement.querySelector('.notification-btn');
      bellBtn?.click();

      expect(togglePopoverSpy).toHaveBeenCalled();
    });

    it('should close notifications when Escape key is pressed while popover is open', () => {
      mockIsPopoverOpen.set(true);
      fixture.detectChanges();

      const event = new KeyboardEvent('keydown', { key: 'Escape' });
      window.dispatchEvent(event);

      expect(closePopoverSpy).toHaveBeenCalled();
    });
  });
});
