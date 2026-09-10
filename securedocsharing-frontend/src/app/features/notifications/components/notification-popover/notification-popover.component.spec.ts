import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router } from '@angular/router';
import { NotificationPopoverComponent } from './notification-popover.component';
import { NotificationStore } from '../../state/notification.store';
import { WorkspaceContextStore } from '../../../../state/workspace/workspace-context.store';
import {
  NotificationResponseDto,
  NotificationStatus,
} from '../../../../core/models/notification-feed.model';
import { OrganizationModel } from '../../../../core/models/organization.model';
import { ProjectWorkspaceModel } from '../../../../core/models/project-workspace.model';

describe('NotificationPopoverComponent', () => {
  let component: NotificationPopoverComponent;
  let fixture: ComponentFixture<NotificationPopoverComponent>;

  let mockNotifications: ReturnType<typeof signal<NotificationResponseDto[]>>;
  let mockActionRequired: ReturnType<typeof signal<NotificationResponseDto[]>>;
  let mockInvitations: ReturnType<typeof signal<NotificationResponseDto[]>>;
  let mockUnreadCount: ReturnType<typeof signal<number>>;
  let mockIsLoading: ReturnType<typeof signal<boolean>>;
  let mockError: ReturnType<typeof signal<string | null>>;

  let markAllAsReadSpy: jasmine.Spy;
  let markAsReadSpy: jasmine.Spy;
  let markAsUnreadSpy: jasmine.Spy;
  let loadNotificationsSpy: jasmine.Spy;

  let routerNavigateSpy: jasmine.Spy;
  let mockActiveOrg: ReturnType<typeof signal<OrganizationModel | null>>;
  let mockActiveWs: ReturnType<typeof signal<ProjectWorkspaceModel | null>>;

  const sampleActionRequired: NotificationResponseDto = {
    id: 1,
    recipientId: 'user-1',
    type: 'ACCESS_REQUEST_SUBMITTED',
    category: 'ACCESS_REQUEST',
    priority: 'HIGH',
    title: 'Access Request Submitted',
    body: 'User requested View access to sensitive-doc.pdf',
    status: 'UNREAD',
    sourceType: 'ACCESS_REQUEST',
    sourceId: '101',
    createdAt: new Date().toISOString(),
  };

  const sampleInvitation: NotificationResponseDto = {
    id: 2,
    recipientId: 'user-1',
    type: 'ORG_INVITATION_ACCEPTED',
    category: 'INVITATION',
    priority: 'MEDIUM',
    title: 'Invitation Accepted',
    body: 'John Doe accepted the invitation',
    status: 'READ',
    sourceType: 'ORGANIZATION',
    sourceId: '202',
    createdAt: new Date(Date.now() - 3600000).toISOString(),
  };

  const sampleRecent: NotificationResponseDto = {
    id: 3,
    recipientId: 'user-1',
    type: 'PERMISSION_GRANTED',
    category: 'SHARING',
    priority: 'LOW',
    title: 'Permission Granted',
    body: 'You received View access to Project Alpha',
    status: 'READ',
    sourceType: 'PERMISSION',
    sourceId: '303',
    createdAt: new Date(Date.now() - 7200000).toISOString(),
  };

  beforeEach(async () => {
    mockNotifications = signal<NotificationResponseDto[]>([
      sampleActionRequired,
      sampleInvitation,
      sampleRecent,
    ]);
    mockActionRequired = signal<NotificationResponseDto[]>([sampleActionRequired]);
    mockInvitations = signal<NotificationResponseDto[]>([sampleInvitation]);
    mockUnreadCount = signal<number>(1);
    mockIsLoading = signal<boolean>(false);
    mockError = signal<string | null>(null);

    markAllAsReadSpy = jasmine.createSpy('markAllAsRead');
    markAsReadSpy = jasmine.createSpy('markAsRead');
    markAsUnreadSpy = jasmine.createSpy('markAsUnread');
    loadNotificationsSpy = jasmine.createSpy('loadNotifications');

    const mockStore = {
      notifications: mockNotifications,
      actionRequiredNotifications: mockActionRequired,
      invitationNotifications: mockInvitations,
      unreadCount: mockUnreadCount,
      isLoading: mockIsLoading,
      error: mockError,
      hasNotifications: () => mockNotifications().length > 0,
      markAllAsRead: markAllAsReadSpy,
      markAsRead: markAsReadSpy,
      markAsUnread: markAsUnreadSpy,
      loadNotifications: loadNotificationsSpy,
    };

    routerNavigateSpy = jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true));
    mockActiveOrg = signal<OrganizationModel | null>(null);
    mockActiveWs = signal<ProjectWorkspaceModel | null>(null);

    await TestBed.configureTestingModule({
      imports: [NotificationPopoverComponent],
      providers: [
        { provide: NotificationStore, useValue: mockStore },
        { provide: Router, useValue: { navigate: routerNavigateSpy } },
        {
          provide: WorkspaceContextStore,
          useValue: {
            activeOrganization: mockActiveOrg,
            activeWorkspace: mockActiveWs,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationPopoverComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create NotificationPopoverComponent', () => {
    expect(component).toBeTruthy();
  });

  it('should display header with unread pill and mark all as read button when unread count > 0', () => {
    const el: HTMLElement = fixture.nativeElement;
    const title = el.querySelector('.header-title');
    expect(title?.textContent).toContain('Notifications');

    const unreadPill = el.querySelector('.unread-pill');
    expect(unreadPill?.textContent).toContain('1 new');

    const markAllBtn = el.querySelector('.action-text-btn');
    expect(markAllBtn).toBeTruthy();
    expect(markAllBtn?.textContent).toContain('Mark all read');
  });

  it('should call store.markAllAsRead when clicking "Mark all read"', () => {
    const markAllBtn: HTMLButtonElement | null = fixture.nativeElement.querySelector('.action-text-btn');
    markAllBtn?.click();

    expect(markAllAsReadSpy).toHaveBeenCalled();
  });

  it('should display Action Required section with actionable item and primary CTA', () => {
    const el: HTMLElement = fixture.nativeElement;
    const actionSection = el.querySelector('.action-required-group');
    expect(actionSection).toBeTruthy();

    const badge = actionSection?.querySelector('.action-count-badge');
    expect(badge?.textContent?.trim()).toBe('1');

    const title = actionSection?.querySelector('.item-title');
    expect(title?.textContent).toContain('Access Request Submitted');

    const ctaBtn = actionSection?.querySelector('.primary-cta-btn');
    expect(ctaBtn).toBeTruthy();
    expect(ctaBtn?.textContent).toContain('Review Request');
  });

  it('should display Invitations & Collaboration section for non-actionable invitation notifications', () => {
    const el: HTMLElement = fixture.nativeElement;
    const invitationHeader = Array.from(el.querySelectorAll('.section-title')).find(
      (h) => h.textContent?.includes('Invitations & Collaboration')
    );
    expect(invitationHeader).toBeTruthy();

    const invitationTitle = el.querySelector('.invitation-icon-avatar')?.closest('.notification-item')?.querySelector('.item-title');
    expect(invitationTitle?.textContent).toContain('Invitation Accepted');
  });

  it('should display Recent Updates section for other notifications', () => {
    const el: HTMLElement = fixture.nativeElement;
    const recentHeader = Array.from(el.querySelectorAll('.section-title')).find(
      (h) => h.textContent?.includes('Recent Updates')
    );
    expect(recentHeader).toBeTruthy();

    const recentTitle = el.querySelector('.general-icon-avatar')?.closest('.notification-item')?.querySelector('.item-title');
    expect(recentTitle?.textContent).toContain('Permission Granted');
  });

  it('should trigger store.markAsRead and navigate when clicking an unread notification item', () => {
    let closed = false;
    component.close.subscribe(() => {
      closed = true;
    });

    const actionItem: HTMLElement | null = fixture.nativeElement.querySelector('.action-item');
    actionItem?.click();

    expect(markAsReadSpy).toHaveBeenCalledWith(1);
    expect(routerNavigateSpy).toHaveBeenCalled();
    expect(closed).toBeTrue();
  });

  it('should toggle read/unread status without triggering item navigation when clicking status toggle button', () => {
    const toggleBtn: HTMLButtonElement | null = fixture.nativeElement.querySelector('.status-toggle-btn');
    toggleBtn?.click();

    expect(markAsReadSpy).toHaveBeenCalledWith(1);
    expect(routerNavigateSpy).not.toHaveBeenCalled();
  });

  it('should display empty state when there are no notifications', () => {
    mockNotifications.set([]);
    mockActionRequired.set([]);
    mockInvitations.set([]);
    mockUnreadCount.set(0);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const emptyState = el.querySelector('.empty-state');
    expect(emptyState).toBeTruthy();
    expect(emptyState?.textContent).toContain('No notifications yet');

    const caughtUpPill = el.querySelector('.caught-up-pill');
    expect(caughtUpPill?.textContent).toContain('All caught up');
  });

  it('should display loading skeleton when loading and empty', () => {
    mockNotifications.set([]);
    mockActionRequired.set([]);
    mockInvitations.set([]);
    mockIsLoading.set(true);
    fixture.detectChanges();

    const skeleton = fixture.nativeElement.querySelector('.skeleton-container');
    expect(skeleton).toBeTruthy();
    const cards = fixture.nativeElement.querySelectorAll('.skeleton-card');
    expect(cards.length).toBe(3);
  });

  it('should display error state with retry button when error occurs', () => {
    mockNotifications.set([]);
    mockActionRequired.set([]);
    mockInvitations.set([]);
    mockError.set('Network connection failed');
    fixture.detectChanges();

    const errorState = fixture.nativeElement.querySelector('.error-state');
    expect(errorState).toBeTruthy();
    expect(errorState?.textContent).toContain('Network connection failed');

    const retryBtn: HTMLButtonElement | null = errorState.querySelector('.retry-btn');
    retryBtn?.click();
    expect(loadNotificationsSpy).toHaveBeenCalledWith(0, true);
  });

  it('should emit close and navigate when clicking "View all notifications"', () => {
    let closed = false;
    component.close.subscribe(() => {
      closed = true;
    });

    const viewAllBtn: HTMLButtonElement | null = fixture.nativeElement.querySelector('.view-all-btn');
    viewAllBtn?.click();

    expect(routerNavigateSpy).toHaveBeenCalled();
    expect(closed).toBeTrue();
  });

  it('should emit close and navigate when clicking header expand button', () => {
    let closed = false;
    component.close.subscribe(() => {
      closed = true;
    });

    const expandBtn: HTMLButtonElement | null = fixture.nativeElement.querySelector('.expand-btn');
    expect(expandBtn).toBeTruthy();
    expandBtn?.click();

    expect(routerNavigateSpy).toHaveBeenCalledWith(['/w', 'personal', 'notifications']);
    expect(closed).toBeTrue();
  });
});
