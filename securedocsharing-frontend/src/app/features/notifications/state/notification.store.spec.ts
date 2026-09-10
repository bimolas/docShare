import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { NotificationStore } from './notification.store';
import { NotificationFeedApiService } from '../../../core/services/notification-feed-api.service';
import {
  NotificationResponseDto,
  SpringPageResponse,
  UnreadCountResponseDto,
} from '../../../core/models/notification-feed.model';

describe('NotificationStore', () => {
  let store: NotificationStore;
  let mockApi: jasmine.SpyObj<NotificationFeedApiService>;

  const sampleNotifications: NotificationResponseDto[] = [
    {
      id: 1,
      recipientId: 'user-1',
      type: 'ACCESS_REQUEST_SUBMITTED',
      category: 'ACCESS_REQUEST',
      priority: 'HIGH',
      title: 'Access Request Submitted',
      body: 'User requested View access to doc.pdf',
      status: 'UNREAD',
      sourceType: 'ACCESS_REQUEST',
      sourceId: '101',
      createdAt: '2026-03-31T10:00:00Z',
    },
    {
      id: 2,
      recipientId: 'user-1',
      type: 'ORG_INVITATION_SENT',
      category: 'INVITATION',
      priority: 'MEDIUM',
      title: 'Organization Invitation',
      body: 'You were invited to Acme Corp',
      status: 'UNREAD',
      sourceType: 'ORGANIZATION',
      sourceId: 'org-1',
      createdAt: '2026-03-31T11:00:00Z',
    },
    {
      id: 3,
      recipientId: 'user-1',
      type: 'PERMISSION_GRANTED',
      category: 'SHARING',
      priority: 'MEDIUM',
      title: 'Permission Granted',
      body: 'You received View access to Folder A',
      status: 'READ',
      sourceType: 'PERMISSION',
      sourceId: 'perm-1',
      createdAt: '2026-03-31T09:00:00Z',
      readAt: '2026-03-31T09:30:00Z',
    },
  ];

  const mockPageResponse: SpringPageResponse<NotificationResponseDto> = {
    content: sampleNotifications,
    totalElements: 3,
    totalPages: 1,
    size: 20,
    number: 0,
    first: true,
    last: true,
    empty: false,
  };

  beforeEach(() => {
    mockApi = jasmine.createSpyObj('NotificationFeedApiService', [
      'getNotifications',
      'getUnreadCount',
      'markAsRead',
      'markAsUnread',
      'markAllAsRead',
      'archiveNotification',
      'unarchiveNotification',
      'deleteNotification',
    ]);

    mockApi.getNotifications.and.returnValue(of(mockPageResponse));
    mockApi.getUnreadCount.and.returnValue(of({ unreadCount: 2 }));

    TestBed.configureTestingModule({
      providers: [
        NotificationStore,
        { provide: NotificationFeedApiService, useValue: mockApi },
      ],
    });

    store = TestBed.inject(NotificationStore);
  });

  it('should initialize with default state', () => {
    expect(store.notifications()).toEqual([]);
    expect(store.unreadCount()).toBe(0);
    expect(store.isLoading()).toBeFalse();
    expect(store.error()).toBeNull();
    expect(store.activeCategory()).toBeNull();
    expect(store.activeStatus()).toBeNull();
    expect(store.isPopoverOpen()).toBeFalse();
  });

  it('should load notifications and update signals', () => {
    store.loadNotifications(0);

    expect(mockApi.getNotifications).toHaveBeenCalledWith(
      jasmine.objectContaining({ page: 0, size: 20 })
    );
    expect(mockApi.getUnreadCount).toHaveBeenCalled();
    expect(store.notifications().length).toBe(3);
    expect(store.totalElements()).toBe(3);
    expect(store.unreadCount()).toBe(2);
    expect(store.isLoading()).toBeFalse();
    expect(store.hasNotifications()).toBeTrue();
    expect(store.hasUnread()).toBeTrue();
  });

  it('should handle load error gracefully', () => {
    const errorResponse = new HttpErrorResponse({
      status: 500,
      error: { message: 'Internal server error' },
    });
    mockApi.getNotifications.and.returnValue(throwError(() => errorResponse));

    store.loadNotifications(0);

    expect(store.isLoading()).toBeFalse();
    expect(store.error()).toBe('Internal server error');
    expect(store.notifications()).toEqual([]);
  });

  it('should include both UNREAD and READ actionable notifications in actionRequiredNotifications', () => {
    store.loadNotifications(0);

    // Initial state: both are UNREAD
    expect(store.actionRequiredNotifications().length).toBe(2);

    // Mark ACCESS_REQUEST_SUBMITTED (id: 1) as READ
    mockApi.markAsRead.and.returnValue(
      of({ ...sampleNotifications[0], status: 'READ', readAt: '2026-03-31T12:00:00Z' })
    );
    store.markAsRead(1);

    // Verifying the critical requirement: READ + pending action MUST still be Action Required!
    const actionRequired = store.actionRequiredNotifications();
    expect(actionRequired.length).toBe(2);
    const readItem = actionRequired.find((n) => n.id === 1);
    expect(readItem).toBeDefined();
    expect(readItem?.status).toBe('READ');
  });

  it('should exclude ARCHIVED notifications from actionRequiredNotifications', () => {
    store.loadNotifications(0);
    mockApi.archiveNotification.and.returnValue(
      of({ ...sampleNotifications[0], status: 'ARCHIVED', archivedAt: '2026-03-31T12:00:00Z' })
    );

    store.archiveNotification(1);

    const actionRequired = store.actionRequiredNotifications();
    expect(actionRequired.length).toBe(1);
    expect(actionRequired[0].id).toBe(2); // Only ORG_INVITATION_SENT remains
  });

  it('should exclude actionable notifications once resolved via markActionResolved or markNotificationActionResolved', () => {
    store.loadNotifications(0);
    expect(store.actionRequiredNotifications().length).toBe(2);

    // Resolve by sourceType and sourceId
    store.markActionResolved('ACCESS_REQUEST', '101');
    expect(store.actionRequiredNotifications().length).toBe(1);
    expect(store.actionRequiredNotifications()[0].id).toBe(2);

    // Resolve by notificationId
    store.markNotificationActionResolved(2);
    expect(store.actionRequiredNotifications().length).toBe(0);
  });

  it('should automatically exclude actionable notifications when a counter-event is present in feed', () => {
    const notificationsWithCounterEvent: NotificationResponseDto[] = [
      ...sampleNotifications,
      {
        id: 4,
        recipientId: 'user-1',
        type: 'ACCESS_REQUEST_APPROVED',
        category: 'ACCESS_REQUEST',
        priority: 'MEDIUM',
        title: 'Access Request Approved',
        body: 'Access request has been approved',
        status: 'UNREAD',
        sourceType: 'ACCESS_REQUEST',
        sourceId: '101',
        createdAt: '2026-03-31T11:30:00Z',
      },
    ];
    mockApi.getNotifications.and.returnValue(
      of({ ...mockPageResponse, content: notificationsWithCounterEvent })
    );

    store.loadNotifications(0);

    const actionRequired = store.actionRequiredNotifications();
    // ACCESS_REQUEST_SUBMITTED for sourceId 101 should be omitted due to ACCESS_REQUEST_APPROVED
    expect(actionRequired.length).toBe(1);
    expect(actionRequired[0].type).toBe('ORG_INVITATION_SENT');
  });

  it('should exclude notifications when actionPending is explicitly false on DTO', () => {
    const notificationsWithActionPending: NotificationResponseDto[] = [
      {
        ...sampleNotifications[0],
        actionPending: false,
      },
      sampleNotifications[1],
    ];
    mockApi.getNotifications.and.returnValue(
      of({ ...mockPageResponse, content: notificationsWithActionPending })
    );

    store.loadNotifications(0);

    const actionRequired = store.actionRequiredNotifications();
    expect(actionRequired.length).toBe(1);
    expect(actionRequired[0].id).toBe(2);
  });

  it('should correctly compute invitationNotifications', () => {
    store.loadNotifications(0);

    const invitations = store.invitationNotifications();
    expect(invitations.length).toBe(1);
    expect(invitations[0].type).toBe('ORG_INVITATION_SENT');
  });

  it('should filter by category and reload page 0', () => {
    store.setCategoryFilter('TEAM');

    expect(store.activeCategory()).toBe('TEAM');
    expect(store.page()).toBe(0);
    expect(mockApi.getNotifications).toHaveBeenCalledWith(
      jasmine.objectContaining({ category: 'TEAM', page: 0 })
    );
  });

  it('should filter by status and reload page 0', () => {
    store.setStatusFilter('UNREAD');

    expect(store.activeStatus()).toBe('UNREAD');
    expect(store.page()).toBe(0);
    expect(mockApi.getNotifications).toHaveBeenCalledWith(
      jasmine.objectContaining({ status: 'UNREAD', page: 0 })
    );
  });

  it('should mark notification as read and decrement unread count', () => {
    store.loadNotifications(0);
    mockApi.markAsRead.and.returnValue(
      of({ ...sampleNotifications[0], status: 'READ', readAt: '2026-03-31T12:00:00Z' })
    );

    store.markAsRead(1);

    const item = store.notifications().find((n) => n.id === 1);
    expect(item?.status).toBe('READ');
    expect(store.unreadCount()).toBe(1);
  });

  it('should mark notification as unread and increment unread count', () => {
    store.loadNotifications(0);
    mockApi.markAsUnread.and.returnValue(
      of({ ...sampleNotifications[2], status: 'UNREAD' })
    );

    store.markAsUnread(3);

    const item = store.notifications().find((n) => n.id === 3);
    expect(item?.status).toBe('UNREAD');
    expect(store.unreadCount()).toBe(3);
  });

  it('should mark all as read and reset unread count to 0', () => {
    store.loadNotifications(0);
    mockApi.markAllAsRead.and.returnValue(of(undefined));

    store.markAllAsRead();

    expect(store.notifications().every((n) => n.status === 'READ')).toBeTrue();
    expect(store.unreadCount()).toBe(0);
  });

  it('should archive notification and decrement unread count if unread', () => {
    store.loadNotifications(0);
    mockApi.archiveNotification.and.returnValue(
      of({ ...sampleNotifications[0], status: 'ARCHIVED', archivedAt: '2026-03-31T12:00:00Z' })
    );

    store.archiveNotification(1);

    const item = store.notifications().find((n) => n.id === 1);
    expect(item?.status).toBe('ARCHIVED');
    expect(store.unreadCount()).toBe(1);
  });

  it('should delete notification and remove it from store', () => {
    store.loadNotifications(0);
    mockApi.deleteNotification.and.returnValue(of(undefined));

    store.deleteNotification(1);

    expect(store.notifications().find((n) => n.id === 1)).toBeUndefined();
    expect(store.totalElements()).toBe(2);
    expect(store.unreadCount()).toBe(1);
  });

  it('should manage popover state correctly', () => {
    expect(store.isPopoverOpen()).toBeFalse();

    store.openPopover();
    expect(store.isPopoverOpen()).toBeTrue();
    expect(mockApi.getNotifications).toHaveBeenCalled();

    store.closePopover();
    expect(store.isPopoverOpen()).toBeFalse();

    store.togglePopover();
    expect(store.isPopoverOpen()).toBeTrue();

    store.togglePopover();
    expect(store.isPopoverOpen()).toBeFalse();
  });
});
