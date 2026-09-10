import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  PermissionEntry,
  EffectivePermissionsResponse,
  GrantPermissionRequest,
  BulkGrantRequest,
  OrgWideGrantRequest,
  UpdatePermissionRequest,
  SharedWithMeItem,
  ResourceType,
  PermissionRequestDto,
  BulkShareWithExclusionsDto,
  PermissionResponseDto,
  ManageAccessSummaryDto,
} from '../interfaces/permission.interfaces';
import { PagedResponse } from '../../../core/models/api-response.model';

/**
 * PermissionApiService: All HTTP calls for permission management.
 * Covers: effective permissions, grant (individual/bulk/org), revoke, update, manage access summary.
 */
@Injectable({ providedIn: 'root' })
export class PermissionApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/permissions`;

  getEffectivePermissions(resourceId: string | number, resourceType: ResourceType | string): Observable<EffectivePermissionsResponse> {
    const targetType = (resourceType === 'document' || resourceType === 'FILE' || resourceType === 'DOCUMENT') ? 'DOCUMENT' : 'FOLDER';
    return this.http.get<EffectivePermissionsResponse>(`${this.base}/effective`, {
      params: { targetId: resourceId.toString(), targetType },
      headers: { 'X-Silent-Error': 'true' },
    });
  }

  getManageAccessSummary(resourceId: string | number, resourceType: ResourceType | string): Observable<ManageAccessSummaryDto> {
    const rType = (resourceType === 'document' || resourceType === 'FILE' || resourceType === 'DOCUMENT') ? 'DOCUMENT' : 'FOLDER';
    return this.http.get<ManageAccessSummaryDto>(`${this.base}/manage`, {
      params: { resourceId: resourceId.toString(), resourceType: rType },
    });
  }

  grantPermission(request: PermissionRequestDto): Observable<PermissionResponseDto> {
    return this.http.post<PermissionResponseDto>(`${this.base}`, request);
  }

  grantFolderShareWithExclusions(request: BulkShareWithExclusionsDto): Observable<PermissionResponseDto[]> {
    return this.http.post<PermissionResponseDto[]>(`${this.base}/grant-with-exclusions`, request);
  }

  getActivePermissionsForFolder(folderId: number): Observable<PermissionResponseDto[]> {
    return this.http.get<PermissionResponseDto[]>(`${this.base}/folder/${folderId}`);
  }

  grantBulk(request: BulkGrantRequest): Observable<PermissionEntry[]> {
    return this.http.post<PermissionEntry[]>(`${this.base}/grant-with-exclusions`, request);
  }

  grantOrgWide(request: OrgWideGrantRequest): Observable<PermissionEntry[]> {
    return this.http.post<PermissionEntry[]>(`${this.base}/grant-org-wide`, request);
  }

  updatePermission(id: string | number, request: UpdatePermissionRequest): Observable<PermissionEntry> {
    return this.http.patch<PermissionEntry>(`${this.base}/${id}`, request);
  }

  revokePermission(id: string | number): Observable<PermissionEntry | void> {
    return this.http.delete<PermissionEntry | void>(`${this.base}/${id}`);
  }

  activatePermission(id: string | number): Observable<PermissionResponseDto> {
    return this.http.patch<PermissionResponseDto>(`${this.base}/${id}/activate`, {});
  }

  suspendPermission(id: string | number): Observable<PermissionResponseDto> {
    return this.http.patch<PermissionResponseDto>(`${this.base}/${id}/suspend`, {});
  }

  getSharedWithMe(page = 0, size = 20): Observable<PagedResponse<SharedWithMeItem>> {
    return this.http.get<PagedResponse<SharedWithMeItem>>(`${this.base}/shared-with-me`, {
      params: { page, size },
    });
  }

  getSharedByMe(page = 0, size = 20): Observable<PagedResponse<PermissionEntry>> {
    return this.http.get<PagedResponse<PermissionEntry>>(`${this.base}/shared-by-me`, {
      params: { page, size },
    });
  }

  getAllMyGrantedPermissions(page = 0, size = 20): Observable<PagedResponse<PermissionEntry>> {
    return this.http.get<PagedResponse<PermissionEntry>>(`${this.base}/my-grants`, {
      params: { page, size },
    });
  }

  getPermissionsForGroup(groupId: number): Observable<PermissionResponseDto[]> {
    return this.http.get<PermissionResponseDto[]>(`${this.base}/target`, {
      params: { groupId: groupId.toString() },
    });
  }
}
