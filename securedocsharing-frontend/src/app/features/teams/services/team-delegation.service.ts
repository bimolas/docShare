import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  TeamAccessBoundaryDto,
  TeamMemberAccessDto,
  GrantMemberAccessRequestDto,
  UpdateMemberAccessRequestDto,
  ScopedResourceNodeDto,
} from '../models/team-delegation.models';

/**
 * Service for Team-Scoped Member Access Delegation and Boundary introspection.
 * Communicates exclusively with /api/v1/organizations/{orgCode}/teams/{teamId}/member-access endpoints.
 */
@Injectable({ providedIn: 'root' })
export class TeamDelegationService {
  private readonly http = inject(HttpClient);

  private getBaseUrl(orgCode: string, teamId: number): string {
    return `${environment.apiBaseUrl}/v1/organizations/${encodeURIComponent(orgCode)}/teams/${teamId}/member-access`;
  }

  getBoundary(orgCode: string, teamId: number): Observable<TeamAccessBoundaryDto> {
    return this.http.get<TeamAccessBoundaryDto>(`${this.getBaseUrl(orgCode, teamId)}/boundary`);
  }

  getMemberAccessList(orgCode: string, teamId: number): Observable<TeamMemberAccessDto[]> {
    return this.http.get<TeamMemberAccessDto[]>(`${this.getBaseUrl(orgCode, teamId)}`);
  }

  grantMemberAccess(
    orgCode: string,
    teamId: number,
    request: GrantMemberAccessRequestDto
  ): Observable<TeamMemberAccessDto> {
    return this.http.post<TeamMemberAccessDto>(`${this.getBaseUrl(orgCode, teamId)}`, request);
  }

  updateMemberAccess(
    orgCode: string,
    teamId: number,
    permissionId: number,
    request: UpdateMemberAccessRequestDto
  ): Observable<TeamMemberAccessDto> {
    return this.http.put<TeamMemberAccessDto>(
      `${this.getBaseUrl(orgCode, teamId)}/${permissionId}`,
      request
    );
  }

  revokeMemberAccess(orgCode: string, teamId: number, permissionId: number): Observable<void> {
    return this.http.delete<void>(`${this.getBaseUrl(orgCode, teamId)}/${permissionId}`);
  }

  getScopedResources(orgCode: string, teamId: number): Observable<ScopedResourceNodeDto> {
    return this.http.get<ScopedResourceNodeDto>(`${this.getBaseUrl(orgCode, teamId)}/scoped-resources`);
  }
}
