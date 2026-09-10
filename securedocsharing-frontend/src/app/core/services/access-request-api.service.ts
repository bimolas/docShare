import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AccessRequestResponseDto,
  AccessRequestCreateDto,
  AccessRequestEligibilityDto,
  AccessRequestActionDto,
  AccessRequestStatus,
} from '../models/access-request.model';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

@Injectable({ providedIn: 'root' })
export class AccessRequestApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/access-requests`;

  checkEligibility(params: {
    documentId?: number;
    documentFileHash?: string;
    folderId?: number;
  }): Observable<AccessRequestEligibilityDto> {
    let httpParams = new HttpParams();
    if (params.documentId) {
      httpParams = httpParams.set('documentId', params.documentId.toString());
    }
    if (params.documentFileHash) {
      httpParams = httpParams.set('documentFileHash', params.documentFileHash);
    }
    if (params.folderId) {
      httpParams = httpParams.set('folderId', params.folderId.toString());
    }
    return this.http.get<AccessRequestEligibilityDto>(`${this.baseUrl}/eligibility`, { params: httpParams });
  }

  createAccessRequest(dto: AccessRequestCreateDto): Observable<AccessRequestResponseDto> {
    return this.http.post<AccessRequestResponseDto>(this.baseUrl, dto);
  }

  getMyAccessRequests(
    status?: AccessRequestStatus,
    page = 0,
    size = 20,
    search?: string
  ): Observable<PageResponse<AccessRequestResponseDto>> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    if (status) {
      params = params.set('status', status);
    }
    if (search && search.trim()) {
      params = params.set('search', search.trim());
    }
    return this.http.get<PageResponse<AccessRequestResponseDto>>(`${this.baseUrl}/my`, { params });
  }

  getReviews(filter?: {
    status?: AccessRequestStatus;
    orgCode?: string;
    workspaceId?: number;
    search?: string;
    page?: number;
    size?: number;
  }): Observable<PageResponse<AccessRequestResponseDto>> {
    let params = new HttpParams()
      .set('page', (filter?.page ?? 0).toString())
      .set('size', (filter?.size ?? 20).toString());
    if (filter?.status) {
      params = params.set('status', filter.status);
    }
    if (filter?.orgCode) {
      params = params.set('orgCode', filter.orgCode);
    }
    if (filter?.workspaceId) {
      params = params.set('workspaceId', filter.workspaceId.toString());
    }
    if (filter?.search && filter.search.trim()) {
      params = params.set('search', filter.search.trim());
    }
    return this.http.get<PageResponse<AccessRequestResponseDto>>(`${this.baseUrl}/reviews`, { params });
  }

  getPendingReviews(page = 0, size = 20): Observable<PageResponse<AccessRequestResponseDto>> {
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http.get<PageResponse<AccessRequestResponseDto>>(`${this.baseUrl}/pending-reviews`, { params });
  }

  getAccessRequestById(id: number): Observable<AccessRequestResponseDto> {
    return this.http.get<AccessRequestResponseDto>(`${this.baseUrl}/${id}`);
  }

  approveAccessRequest(id: number, actionDto?: AccessRequestActionDto): Observable<AccessRequestResponseDto> {
    return this.http.post<AccessRequestResponseDto>(`${this.baseUrl}/${id}/approve`, actionDto || {});
  }

  denyAccessRequest(id: number, actionDto?: AccessRequestActionDto): Observable<AccessRequestResponseDto> {
    return this.http.post<AccessRequestResponseDto>(`${this.baseUrl}/${id}/deny`, actionDto || {});
  }

  cancelAccessRequest(id: number): Observable<AccessRequestResponseDto> {
    return this.http.post<AccessRequestResponseDto>(`${this.baseUrl}/${id}/cancel`, {});
  }
}
