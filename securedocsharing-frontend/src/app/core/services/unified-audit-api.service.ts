import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  UnifiedAuditEventResponseDto,
  UnifiedAuditQueryCriteria,
  SpringPageResponse,
} from '../models/unified-audit.model';

@Injectable({ providedIn: 'root' })
export class UnifiedAuditApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/audit/events`;

  /**
   * Queries unified audit events according to the backend security gateway & criteria.
   * Endpoint: GET /api/v1/audit/events
   */
  getEvents(
    criteria: UnifiedAuditQueryCriteria,
    page: number = 0,
    size: number = 20,
    sort: string = 'timestamp,desc'
  ): Observable<SpringPageResponse<UnifiedAuditEventResponseDto>> {
    let params = new HttpParams()
      .set('scopeType', criteria.scopeType)
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);

    if (criteria.orgCode) {
      params = params.set('orgCode', criteria.orgCode);
    }
    if (criteria.workspaceId !== undefined && criteria.workspaceId !== null) {
      params = params.set('workspaceId', criteria.workspaceId.toString());
    }
    if (criteria.folderId !== undefined && criteria.folderId !== null) {
      params = params.set('folderId', criteria.folderId.toString());
    }
    if (criteria.documentId !== undefined && criteria.documentId !== null) {
      params = params.set('documentId', criteria.documentId.toString());
    }
    if (criteria.teamId !== undefined && criteria.teamId !== null) {
      params = params.set('teamId', criteria.teamId.toString());
    }
    if (criteria.collaborationId !== undefined && criteria.collaborationId !== null) {
      params = params.set('collaborationId', criteria.collaborationId.toString());
    }
    if (criteria.includeDescendants !== undefined && criteria.includeDescendants !== null) {
      params = params.set('includeDescendants', criteria.includeDescendants.toString());
    }
    if (criteria.view) {
      params = params.set('view', criteria.view);
    }
    if (criteria.category) {
      params = params.set('category', criteria.category);
    }
    if (criteria.action) {
      params = params.set('action', criteria.action);
    }
    if (criteria.actor) {
      params = params.set('actor', criteria.actor);
    }
    if (criteria.search) {
      params = params.set('search', criteria.search.trim());
    }
    if (criteria.from) {
      params = params.set('from', criteria.from);
    }
    if (criteria.to) {
      params = params.set('to', criteria.to);
    }

    return this.http.get<SpringPageResponse<UnifiedAuditEventResponseDto>>(this.baseUrl, { params });
  }
}
