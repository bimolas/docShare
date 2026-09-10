import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router } from '@angular/router';
import { NotificationCenterPageComponent } from './notification-center.page';
import { NotificationStore } from '../state/notification.store';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import {
  NotificationResponseDto,
  NotificationStatus,
} from '../../../core/models/notification-feed.model';
import { OrganizationModel } from '../../../core/models/organization.model';
import { ProjectWorkspaceModel } from '../../../core/models/project-workspace.model';

describe('NotificationCenterPageComponent', () => {
  let component: NotificationCenterPageComponent;
  let fixture: ComponentFixture<NotificationCenterPageComponent>;

  let mockNotifications: ReturnType<typeof signal<NotificationResponseDto[]>>;
  let mockActionRequired: ReturnType<typeof signal<NotificationResponseDto[]>>;
  let mockUnreadCount: ReturnType<typeof signal<number>>;
  let mockTotalElements: ReturnType<typeof signal<number>>;
  let mockTotalPages: ReturnType<typeof signal<number>>;
  let mockPage: ReturnType<typeof signal<number>>;
  let mockIsLoading: ReturnType<typeof signal<boolean>>;
  let mockIsRefreshing: ReturnType<typeof signal<boolean>>;
  let mockError: ReturnType<typeof signal<string | null>>;

  let loadNotificationsSpy: jasmine.Spy;
  let refreshUnreadCountSpy: jasmine.Spy;
  let markAllAsReadSpy: jasmine.Spy;
  let markAsReadSpy: jasmine.Spy;
  let markAsUnreadSpy: jasmine.Spy;
  let archiveNotificationSpy: jasmine.Spy;
  let unarchiveNotificationSpy: jasmine.Spy;
  let deleteNotificationSpy: jasmine.Spy;
  let setCategoryFilterSpy: jasmine.Spy;
  let setStatusFilterSpy: jasmine.Spy;

  let routerNavigateSpy: jasmine.Spy;
  let mockActiveOrg: ReturnType<typeof signal<OrganizationModel | null>>;
  let mockActiveWs: ReturnType<typeof signal<ProjectWorkspaceModel | null>>;

  const sampleActionRequiredItem: NotificationResponseDto = {
    id: 101,
    recipientId: 'user-1',
    type: 'ACCESS_REQUEST_SUBMITTED',
    category: 'ACCESS_REQUEST',
    priority: 'HIGH',
    title: 'Access Request Submitted',
    body: 'Review request for workspace document access',
    status: 'UNREAD',
    sourceType: 'ACCESS_REQUEST',
    sourceId: 'req-1',
    createdAt: '2026-03-31T10:00:00Z',
  };

  const sampleInvitationItem: NotificationResponseDto = {
    id: 102,
    recipientId: 'user-1',
    type: 'ORG_INVITATION_SENT',
    category: 'INVITATION',
    priority: 'MEDIUM',
    title: 'Organization Invitation',
    body: 'You were invited to Acme Inc',
    status: 'UNREAD',
    sourceType: 'ORGANIZATION',
    sourceId: 'inv-1',
    createdAt: '2026-03-31T11:00:00Z',
  };

  const sampleSharingItem: NotificationResponseDto = {
    id: 103,
    recipientId: 'user-1',
    type: 'PERMISSION_GRANTED',
    category: 'SHARING',
    priority: 'LOW',
    title: 'Permission Granted',
    body: 'Read access granted to Folder X',
    status: 'READ',
    sourceType: 'PERMISSION',
    sourceId: 'perm-1',
    createdAt: '2026-03-31T09:00:00Z',
  };

  beforeEach(async () => {
    mockNotifications = signal<NotificationResponseDto[]>([
      sampleActionRequiredItem,
      sampleInvitationItem,
      sampleSharingItem,
    ]);
    mockActionRequired = signal<NotificationResponseDto[]>([
      sampleActionRequiredItem,
      sampleInvitationItem,
    ]);
    mockUnreadCount = signal<number>(2);
    mockTotalElements = signal<number>(3);
    mockTotalPages = signal<number>(1);
    mockPage = signal<number>(0);
    mockIsLoading = signal<boolean>(false);
    mockIsRefreshing = signal<boolean>(false);
    mockError = signal<string | null>(null);

    loadNotificationsSpy = jasmine.createSpy('loadNotifications');
    refreshUnreadCountSpy = jasmine.createSpy('refreshUnreadCount');
    markAllAsReadSpy = jasmine.createSpy('markAllAsRead');
    markAsReadSpy = jasmine.createSpy('markAsRead');
    markAsUnreadSpy = jasmine.createSpy('markAsUnread');
    archiveNotificationSpy = jasmine.createSpy('archiveNotification');
    unarchiveNotificationSpy = jasmine.createSpy('unarchiveNotification');
    deleteNotificationSpy = jasmine.createSpy('deleteNotification');
    setCategoryFilterSpy = jasmine.createSpy('setCategoryFilter');
    setStatusFilterSpy = jasmine.createSpy('setStatusFilter');

    routerNavigateSpy = jasmine.createSpy('navigate').and.returnValue(Promise.resolve(true));
    mockActiveOrg = signal<OrganizationModel | null>(null);
    mockActiveWs = signal<ProjectWorkspaceModel | null>(null);

    const mockStore = {
      notifications: mockNotifications,
      actionRequiredNotifications: mockActionRequired,
      unreadCount: mockUnreadCount,
      totalElements: mockTotalElements,
      totalPages: mockTotalPages,
      page: mockPage,
      isLoading: mockIsLoading,
      isRefreshing: mockIsRefreshing,
      error: mockError,
      loadNotifications: loadNotificationsSpy,
      refreshUnreadCount: refreshUnreadCountSpy,
      markAllAsRead: markAllAsReadSpy,
      markAsRead: markAsReadSpy,
      markAsUnread: markAsUnreadSpy,
      archiveNotification: archiveNotificationSpy,
      unarchiveNotification: unarchiveNotificationSpy,
      deleteNotification: deleteNotificationSpy,
      setCategoryFilter: setCategoryFilterSpy,
      setStatusFilter: setStatusFilterSpy,
    };

    await TestBed.configureTestingModule({
      imports: [NotificationCenterPageComponent],
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

    fixture = TestBed.createComponent(NotificationCenterPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create NotificationCenterPageComponent and initialize data', () => {
    expect(component).toBeTruthy();
    expect(loadNotificationsSpy).toHaveBeenCalledWith(0);
    expect(refreshUnreadCountSpy).toHaveBeenCalled();
  });

  it('should render header with unread count and mark all read button', () => {
    const el: HTMLElement = fixture.nativeElement;
    const title = el.querySelector('.page-title');
    expect(title?.textContent).toContain('Notification Center');

    const unreadPill = el.querySelector('.unread-count-pill');
    expect(unreadPill?.textContent).toContain('2 unread');

    const markAllBtn = el.querySelector('.btn-secondary');
    expect(markAllBtn?.textContent).toContain('Mark all read');
  });

  it('should call store.markAllAsRead when clicking "Mark all read"', () => {
    const markAllBtn: HTMLButtonElement | null = fixture.nativeElement.querySelector('.btn-secondary');
    markAllBtn?.click();

    expect(markAllAsReadSpy).toHaveBeenCalled();
  });

  it('should render category tabs and switch activeTab on click', () => {
    const el: HTMLElement = fixture.nativeElement;
    const tabs = el.querySelectorAll('.tab-btn');
    expect(tabs.length).toBe(6);

    // Switch to Action Required tab
    const actionTab = Array.from(tabs).find((t) => t.textContent?.includes('Action Required')) as HTMLButtonElement;
    actionTab.click();
    fixture.detectChanges();

    expect(component.activeTab()).toBe('ACTION_REQUIRED');
    expect(setCategoryFilterSpy).toHaveBeenCalledWith(null);

    // Filter to invitations tab
    const inviteTab = Array.from(tabs).find((t) => t.textContent?.includes('Invitations')) as HTMLButtonElement;
    inviteTab.click();
    fixture.detectChanges();

    expect(component.activeTab()).toBe('INVITATIONS');
    expect(setCategoryFilterSpy).toHaveBeenCalledWith('INVITATION');
  });

  it('should filter by status when clicking status chips', () => {
    const el: HTMLElement = fixture.nativeElement;
    const statusChips = el.querySelectorAll('.status-chip');

    // Click Unread chip
    const unreadChip = Array.from(statusChips).find((c) => c.textContent?.includes('Unread')) as HTMLButtonElement;
    unreadChip.click();
    fixture.detectChanges();

    expect(component.activeStatusFilter()).toBe('UNREAD');
    expect(setStatusFilterSpy).toHaveBeenCalledWith('UNREAD');

    // Click Archived chip
    const archivedChip = Array.from(statusChips).find((c) => c.textContent?.includes('Archived')) as HTMLButtonElement;
    archivedChip.click();
    fixture.detectChanges();

    expect(component.activeStatusFilter()).toBe('ARCHIVED');
    expect(setStatusFilterSpy).toHaveBeenCalledWith('ARCHIVED');
  });

  it('should toggle read status when clicking read/unread action button', () => {
    const cards = fixture.nativeElement.querySelectorAll('.notification-card');
    expect(cards.length).toBe(3);

    // First item is UNREAD
    const firstCardReadToggle: HTMLButtonElement | null = cards[0].querySelector('.icon-action-button');
    firstCardReadToggle?.click();

    expect(markAsReadSpy).toHaveBeenCalledWith(101);
  });

  it('should archive and unarchive notification correctly', () => {
    const cards = fixture.nativeElement.querySelectorAll('.notification-card');

    // Click Archive button on first card
    const archiveBtn: HTMLButtonElement | null = Array.from(
      cards[0].querySelectorAll('.icon-action-button')
    ).find((b: any) => b.textContent?.includes('Archive')) as HTMLButtonElement;

    archiveBtn?.click();
    expect(archiveNotificationSpy).toHaveBeenCalledWith(101);
  });

  it('should delete notification when clicking delete button', () => {
    const cards = fixture.nativeElement.querySelectorAll('.notification-card');
    const deleteBtn: HTMLButtonElement | null = cards[0].querySelector('.btn-delete');

    deleteBtn?.click();
    expect(deleteNotificationSpy).toHaveBeenCalledWith(101);
  });

  it('should navigate and mark as read when CTA is clicked', () => {
    const firstCard: HTMLElement = fixture.nativeElement.querySelector('.notification-card');
    const ctaBtn: HTMLButtonElement | null = firstCard.querySelector('.cta-action-btn');

    ctaBtn?.click();
    expect(markAsReadSpy).toHaveBeenCalledWith(101);
    expect(routerNavigateSpy).toHaveBeenCalled();
  });

  it('should navigate to next and previous page when pagination controls are clicked', () => {
    mockTotalPages.set(3);
    mockPage.set(1); // Page 2 of 3
    fixture.detectChanges();

    const paginationControls = fixture.nativeElement.querySelectorAll('.pagination-btn');
    expect(paginationControls.length).toBe(2);

    const prevBtn: HTMLButtonElement = paginationControls[0];
    const nextBtn: HTMLButtonElement = paginationControls[1];

    prevBtn.click();
    expect(loadNotificationsSpy).toHaveBeenCalledWith(0);

    nextBtn.click();
    expect(loadNotificationsSpy).toHaveBeenCalledWith(2);
  });

  it('should show loading skeleton when isLoading is true and no notifications loaded', () => {
    mockNotifications.set([]);
    mockActionRequired.set([]);
    mockIsLoading.set(true);
    fixture.detectChanges();

    const skeleton = fixture.nativeElement.querySelector('.skeleton-list');
    expect(skeleton).toBeTruthy();
  });

  it('should display error card with retry button when error occurs', () => {
    mockNotifications.set([]);
    mockActionRequired.set([]);
    mockError.set('Database connection timeout');
    fixture.detectChanges();

    const errorCard = fixture.nativeElement.querySelector('.error-card');
    expect(errorCard).toBeTruthy();
    expect(errorCard?.textContent).toContain('Database connection timeout');

    const retryBtn: HTMLButtonElement | null = errorCard.querySelector('.btn-primary');
    retryBtn?.click();
    expect(loadNotificationsSpy).toHaveBeenCalled();
  });

  it('should display empty state card when no notifications match', () => {
    mockNotifications.set([]);
    mockActionRequired.set([]);
    fixture.detectChanges();

    const emptyCard = fixture.nativeElement.querySelector('.empty-card');
    expect(emptyCard).toBeTruthy();
    expect(emptyCard?.textContent).toContain('No notifications found');
  });
});
