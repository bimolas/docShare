import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NotificationFeedApiService } from './notification-feed-api.service';
import { environment } from '../../../environments/environment';
import {
  NotificationResponseDto,
  SpringPageResponse,
  UnreadCountResponseDto,
} from '../models/notification-feed.model';

describe('NotificationFeedApiService', () => {
  let service: NotificationFeedApiService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiBaseUrl}/v1/notifications`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [NotificationFeedApiService],
    });
    service = TestBed.inject(NotificationFeedApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should query notifications with category, status, and page parameters', () => {
    const mockPage: SpringPageResponse<NotificationResponseDto> = {
      content: [
        {
          id: 10,
          recipientId: 'user-1',
          type: 'TEAM_ADDED',
          category: 'TEAM',
          priority: 'MEDIUM',
          title: 'Added to Team',
          body: 'You were added to Engineering',
          status: 'UNREAD',
          sourceType: 'TEAM',
          sourceId: '1',
          createdAt: '2026-03-31T12:00:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      size: 10,
      number: 0,
      first: true,
      last: true,
      empty: false,
    };

    service
      .getNotifications({
        category: 'TEAM',
        status: 'UNREAD',
        page: 0,
        size: 10,
        sort: 'createdAt,desc',
      })
      .subscribe((res) => {
        expect(res.content.length).toBe(1);
        expect(res.content[0].type).toBe('TEAM_ADDED');
      });

    const req = httpMock.expectOne((r) => r.url === baseUrl);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('category')).toBe('TEAM');
    expect(req.request.params.get('status')).toBe('UNREAD');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('createdAt,desc');
    req.flush(mockPage);
  });

  it('should fetch unread count', () => {
    const mockCount: UnreadCountResponseDto = { unreadCount: 5 };

    service.getUnreadCount().subscribe((res) => {
      expect(res.unreadCount).toBe(5);
    });

    const req = httpMock.expectOne(`${baseUrl}/unread-count`);
    expect(req.request.method).toBe('GET');
    req.flush(mockCount);
  });

  it('should patch mark as read', () => {
    const mockRes: NotificationResponseDto = {
      id: 1,
      recipientId: 'user-1',
      type: 'PERMISSION_GRANTED',
      category: 'SHARING',
      priority: 'MEDIUM',
      title: 'Granted',
      body: 'Access granted',
      status: 'READ',
      sourceType: 'PERMISSION',
      createdAt: '2026-03-31T12:00:00Z',
      readAt: '2026-03-31T12:05:00Z',
    };

    service.markAsRead(1).subscribe((res) => {
      expect(res.status).toBe('READ');
    });

    const req = httpMock.expectOne(`${baseUrl}/1/read`);
    expect(req.request.method).toBe('PATCH');
    req.flush(mockRes);
  });

  it('should patch mark all as read', () => {
    service.markAllAsRead().subscribe();

    const req = httpMock.expectOne(`${baseUrl}/read-all`);
    expect(req.request.method).toBe('PATCH');
    req.flush(null);
  });

  it('should archive and delete notification', () => {
    service.archiveNotification(2).subscribe();
    const archiveReq = httpMock.expectOne(`${baseUrl}/2/archive`);
    expect(archiveReq.request.method).toBe('PATCH');
    archiveReq.flush({ id: 2, status: 'ARCHIVED' });

    service.deleteNotification(2).subscribe();
    const deleteReq = httpMock.expectOne(`${baseUrl}/2`);
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush(null);
  });
});
