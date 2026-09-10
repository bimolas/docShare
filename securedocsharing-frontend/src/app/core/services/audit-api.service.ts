import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DocumentActivityLogResponse, PermissionAuditLogResponse } from '../models/audit.model';

@Injectable({ providedIn: 'root' })
export class AuditApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/audit`;

  /**
   * Gets document/folder activity logs.
   * Endpoint: GET /api/v1/audit/activity
   */
  getActivityLogs(documentId?: number, folderId?: number, orgCode?: string): Observable<DocumentActivityLogResponse[]> {
    let params = new HttpParams();
    if (documentId) params = params.set('documentId', documentId.toString());
    if (folderId) params = params.set('folderId', folderId.toString());
    if (orgCode) params = params.set('orgCode', orgCode);

    return this.http.get<DocumentActivityLogResponse[]>(`${this.baseUrl}/activity`, { params });
  }

  /**
   * Gets permission audit logs.
   * Endpoint: GET /api/v1/audit/permissions
   */
  getPermissionAuditLogs(targetId?: number, targetType?: string, folderId?: number, orgCode?: string): Observable<PermissionAuditLogResponse[]> {
    let params = new HttpParams();
    if (targetId) params = params.set('targetId', targetId.toString());
    if (targetType) params = params.set('targetType', targetType);
    if (folderId) params = params.set('folderId', folderId.toString());
    if (orgCode) params = params.set('orgCode', orgCode);

    return this.http.get<PermissionAuditLogResponse[]>(`${this.baseUrl}/permissions`, { params });
  }
}
