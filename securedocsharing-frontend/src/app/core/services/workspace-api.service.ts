import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  ProjectWorkspaceModel,
  ProjectWorkspaceResponseDto,
  CreateProjectWorkspaceRequestDto,
  CreatePersonalWorkspaceRequestDto,
  UpdateProjectWorkspaceRequestDto,
  WorkspaceAssignmentResponseDto,
  WorkspaceTeamAssignmentRequestDto,
  WorkspaceUserAssignmentRequestDto,
  WorkspacePolicyDto,
  toProjectWorkspaceModel,
  toProjectWorkspaceModels,
} from '../models/project-workspace.model';

/**
 * Authoritative Canonical REST API client for Project Workspace management.
 * All workspace operations use real numeric workspace IDs and canonical backend endpoints.
 */
@Injectable({ providedIn: 'root' })
export class WorkspaceApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1`;

  /**
   * Fetches all accessible personal workspaces for the current authenticated user.
   * Endpoint: GET /api/v1/personal/workspaces
   */
  getPersonalWorkspaces(): Observable<ProjectWorkspaceModel[]> {
    return this.http
      .get<ProjectWorkspaceResponseDto[]>(`${this.baseUrl}/personal/workspaces`)
      .pipe(map(toProjectWorkspaceModels));
  }

  /**
   * Creates a new personal workspace for the current authenticated user.
   * Endpoint: POST /api/v1/personal/workspaces
   */
  createPersonalWorkspace(
    request: CreatePersonalWorkspaceRequestDto
  ): Observable<ProjectWorkspaceModel> {
    return this.http
      .post<ProjectWorkspaceResponseDto>(`${this.baseUrl}/personal/workspaces`, request)
      .pipe(map(toProjectWorkspaceModel));
  }

  /**
   * Fetches all accessible workspaces for an organization.
   * Endpoint: GET /api/v1/organizations/{orgCode}/workspaces
   */
  getWorkspacesForOrganization(orgCode: string): Observable<ProjectWorkspaceModel[]> {
    return this.http
      .get<ProjectWorkspaceResponseDto[]>(`${this.baseUrl}/organizations/${orgCode}/workspaces`)
      .pipe(map(toProjectWorkspaceModels));
  }

  /**
   * Creates a new project workspace in an organization.
   * Endpoint: POST /api/v1/organizations/{orgCode}/workspaces
   */
  createWorkspace(
    orgCode: string,
    request: CreateProjectWorkspaceRequestDto
  ): Observable<ProjectWorkspaceModel> {
    const payload = { ...request, orgCode: request.orgCode || orgCode };
    return this.http
      .post<ProjectWorkspaceResponseDto>(`${this.baseUrl}/organizations/${orgCode}/workspaces`, payload)
      .pipe(map(toProjectWorkspaceModel));
  }

  /**
   * Fetches workspace details by numeric workspace ID.
   * Endpoint: GET /api/v1/workspaces/{workspaceId}
   */
  getWorkspaceById(workspaceId: number): Observable<ProjectWorkspaceModel> {
    return this.http
      .get<ProjectWorkspaceResponseDto>(`${this.baseUrl}/workspaces/${workspaceId}`)
      .pipe(map(toProjectWorkspaceModel));
  }

  /**
   * Updates workspace name and/or description.
   * Endpoint: PATCH /api/v1/workspaces/{workspaceId}
   */
  updateWorkspace(
    workspaceId: number,
    request: UpdateProjectWorkspaceRequestDto
  ): Observable<ProjectWorkspaceModel> {
    return this.http
      .patch<ProjectWorkspaceResponseDto>(`${this.baseUrl}/workspaces/${workspaceId}`, request)
      .pipe(map(toProjectWorkspaceModel));
  }

  /**
   * Archives an active workspace.
   * Endpoint: POST /api/v1/workspaces/{workspaceId}/archive
   */
  archiveWorkspace(workspaceId: number): Observable<ProjectWorkspaceModel> {
    return this.http
      .post<ProjectWorkspaceResponseDto>(`${this.baseUrl}/workspaces/${workspaceId}/archive`, {})
      .pipe(map(toProjectWorkspaceModel));
  }

  /**
   * Restores an archived workspace to active status.
   * Endpoint: POST /api/v1/workspaces/{workspaceId}/restore
   */
  restoreWorkspace(workspaceId: number): Observable<ProjectWorkspaceModel> {
    return this.http
      .post<ProjectWorkspaceResponseDto>(`${this.baseUrl}/workspaces/${workspaceId}/restore`, {})
      .pipe(map(toProjectWorkspaceModel));
  }

  /**
   * Lists direct team and user assignments within a workspace.
   * Endpoint: GET /api/v1/workspaces/{workspaceId}/assignments
   */
  getWorkspaceAssignments(workspaceId: number): Observable<WorkspaceAssignmentResponseDto[]> {
    return this.http.get<WorkspaceAssignmentResponseDto[]>(
      `${this.baseUrl}/workspaces/${workspaceId}/assignments`
    );
  }

  /**
   * Assigns a team to a folder within a workspace.
   * Endpoint: POST /api/v1/workspaces/{workspaceId}/assignments/teams
   */
  assignTeamToWorkspace(
    workspaceId: number,
    request: WorkspaceTeamAssignmentRequestDto
  ): Observable<WorkspaceAssignmentResponseDto> {
    return this.http.post<WorkspaceAssignmentResponseDto>(
      `${this.baseUrl}/workspaces/${workspaceId}/assignments/teams`,
      request
    );
  }

  /**
   * Assigns a user directly to a folder within a workspace.
   * Endpoint: POST /api/v1/workspaces/{workspaceId}/assignments/users
   */
  assignUserToWorkspace(
    workspaceId: number,
    request: WorkspaceUserAssignmentRequestDto
  ): Observable<WorkspaceAssignmentResponseDto> {
    return this.http.post<WorkspaceAssignmentResponseDto>(
      `${this.baseUrl}/workspaces/${workspaceId}/assignments/users`,
      request
    );
  }

  /**
   * Revokes a workspace assignment by permission ID.
   * Endpoint: DELETE /api/v1/workspaces/{workspaceId}/assignments/{permissionId}
   */
  revokeWorkspaceAssignment(workspaceId: number, permissionId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/workspaces/${workspaceId}/assignments/${permissionId}`
    );
  }

  /**
   * Fetches the policy for a workspace.
   * Endpoint: GET /api/v1/workspaces/{workspaceId}/policies
   */
  getWorkspacePolicy(workspaceId: number): Observable<WorkspacePolicyDto> {
    return this.http.get<WorkspacePolicyDto>(`${this.baseUrl}/workspaces/${workspaceId}/policies`);
  }

  /**
   * Updates the policy for a workspace.
   * Endpoint: PUT /api/v1/workspaces/{workspaceId}/policies
   */
  updateWorkspacePolicy(workspaceId: number, policy: WorkspacePolicyDto): Observable<WorkspacePolicyDto> {
    return this.http.put<WorkspacePolicyDto>(`${this.baseUrl}/workspaces/${workspaceId}/policies`, policy);
  }

  /**
   * Fetches the policy for an organization.
   * Endpoint: GET /api/v1/organizations/{orgCode}/policies
   */
  getOrganizationPolicy(orgCode: string): Observable<WorkspacePolicyDto> {
    return this.http.get<WorkspacePolicyDto>(`${this.baseUrl}/organizations/${orgCode}/policies`);
  }

  /**
   * Updates the policy for an organization.
   * Endpoint: PUT /api/v1/organizations/{orgCode}/policies
   */
  updateOrganizationPolicy(orgCode: string, policy: WorkspacePolicyDto): Observable<WorkspacePolicyDto> {
    return this.http.put<WorkspacePolicyDto>(`${this.baseUrl}/organizations/${orgCode}/policies`, policy);
  }
}
