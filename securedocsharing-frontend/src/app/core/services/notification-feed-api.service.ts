import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  NotificationResponseDto,
  UnreadCountResponseDto,
  NotificationQueryParams,
  SpringPageResponse,
} from '../models/notification-feed.model';

@Injectable({ providedIn: 'root' })
export class NotificationFeedApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/notifications`;

  getNotifications(
    params?: NotificationQueryParams
  ): Observable<SpringPageResponse<NotificationResponseDto>> {
    let httpParams = new HttpParams();

    if (params) {
      if (params.status) {
        httpParams = httpParams.set('status', params.status);
      }
      if (params.sourceType) {
        httpParams = httpParams.set('sourceType', params.sourceType);
      }
      if (params.category) {
        httpParams = httpParams.set('category', params.category);
      }
      if (params.page !== undefined && params.page !== null) {
        httpParams = httpParams.set('page', params.page.toString());
      }
      if (params.size !== undefined && params.size !== null) {
        httpParams = httpParams.set('size', params.size.toString());
      }
      if (params.sort) {
        httpParams = httpParams.set('sort', params.sort);
      }
    }

    return this.http.get<SpringPageResponse<NotificationResponseDto>>(this.baseUrl, {
      params: httpParams,
    });
  }

  getUnreadCount(): Observable<UnreadCountResponseDto> {
    return this.http.get<UnreadCountResponseDto>(`${this.baseUrl}/unread-count`);
  }

  markAsRead(id: number): Observable<NotificationResponseDto> {
    return this.http.patch<NotificationResponseDto>(`${this.baseUrl}/${id}/read`, {});
  }

  markAsUnread(id: number): Observable<NotificationResponseDto> {
    return this.http.patch<NotificationResponseDto>(`${this.baseUrl}/${id}/unread`, {});
  }

  markAllAsRead(): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/read-all`, {});
  }

  archiveNotification(id: number): Observable<NotificationResponseDto> {
    return this.http.patch<NotificationResponseDto>(`${this.baseUrl}/${id}/archive`, {});
  }

  unarchiveNotification(id: number): Observable<NotificationResponseDto> {
    return this.http.patch<NotificationResponseDto>(`${this.baseUrl}/${id}/unarchive`, {});
  }

  deleteNotification(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
