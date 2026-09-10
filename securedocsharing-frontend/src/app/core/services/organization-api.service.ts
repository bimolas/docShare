import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  OrganizationResponseDto,
  OrganizationRequestDto,
  OrganizationMemberResponseDto,
  OrganizationMemberDetailResponseDto,
  AddMemberRequestDto,
  UpdateMemberRoleRequestDto,
  TransferOrgAdminRequestDto,
  MemberStatusChangeRequestDto,
  GroupResponseDto,
  GroupRequestDto,
  UpdateGroupMemberRoleRequestDto,
  CreateOrganizationInvitationRequestDto,
  OrganizationInvitationResponseDto,
  MemberAccessSummaryResponseDto,
} from '../models/organization.model';

@Injectable({ providedIn: 'root' })
export class OrganizationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/organizations`;

  readonly pendingInvitationsCount = signal<number>(0);

  /**
   * Refreshes pending invitations count for the current user.
   */
  refreshPendingCount(): void {
    this.getMyPendingInvitations().subscribe({
      next: (invites) => {
        const count = (invites || []).filter((inv) => inv.status === 'PENDING').length;
        this.pendingInvitationsCount.set(count);
      },
      error: () => {
        // Silently preserve current state on error
      },
    });
  }

  /**
   * Retrieves single organization member detail.
   * Endpoint: GET /api/v1/organizations/{orgCode}/members/{userId}/details
   */
  getSingleMemberDetail(
    orgCode: string,
    userId: string
  ): Observable<OrganizationMemberDetailResponseDto> {
    return this.http.get<OrganizationMemberDetailResponseDto>(
      `${this.baseUrl}/${orgCode}/members/${userId}/details`
    );
  }

  /**
   * Retrieves organization member access summary (teams, workspaces, permissions).
   * Endpoint: GET /api/v1/organizations/{orgCode}/members/{userId}/access-summary
   */
  getMemberAccessSummary(
    orgCode: string,
    userId: string
  ): Observable<MemberAccessSummaryResponseDto> {
    return this.http.get<MemberAccessSummaryResponseDto>(
      `${this.baseUrl}/${orgCode}/members/${userId}/access-summary`
    );
  }

  /**
   * Retrieves all organizations user belongs to.
   * Endpoint: GET /api/v1/organizations
   */
  getUserOrganizations(): Observable<OrganizationResponseDto[]> {
    return this.http.get<OrganizationResponseDto[]>(this.baseUrl);
  }

  /**
   * Searches organizations.
   * Endpoint: GET /api/v1/organizations/search?q={query}
   */
  searchOrganizations(query?: string): Observable<OrganizationResponseDto[]> {
    let params = new HttpParams();
    if (query) {
      params = params.set('q', query);
    }
    return this.http.get<OrganizationResponseDto[]>(`${this.baseUrl}/search`, { params });
  }

  /**
   * Gets groups for an organization.
   * Endpoint: GET /api/v1/organizations/{orgCode}/groups
   */
  getOrganizationGroups(orgCode: string): Observable<GroupResponseDto[]> {
    return this.http.get<GroupResponseDto[]>(`${this.baseUrl}/${orgCode}/groups`);
  }

  /**
   * Gets group details by ID.
   * Endpoint: GET /api/v1/organizations/{orgCode}/groups/{groupId}
   */
  getGroupById(orgCode: string, groupId: number): Observable<GroupResponseDto> {
    return this.http.get<GroupResponseDto>(`${this.baseUrl}/${orgCode}/groups/${groupId}`);
  }

  /**
   * Gets members of a team group.
   * Endpoint: GET /api/v1/organizations/{orgCode}/groups/{groupId}/members
   */
  getGroupMembers(orgCode: string, groupId: number): Observable<OrganizationMemberResponseDto[]> {
    return this.http.get<OrganizationMemberResponseDto[]>(`${this.baseUrl}/${orgCode}/groups/${groupId}/members`);
  }

  /**
   * Creates a new team group in an organization.
   * Endpoint: POST /api/v1/organizations/{orgCode}/groups
   */
  createGroup(orgCode: string, request: GroupRequestDto): Observable<GroupResponseDto> {
    return this.http.post<GroupResponseDto>(`${this.baseUrl}/${orgCode}/groups`, request);
  }

  /**
   * Updates an existing team group.
   * Endpoint: PUT /api/v1/organizations/{orgCode}/groups/{groupId}
   */
  updateGroup(orgCode: string, groupId: number, request: GroupRequestDto): Observable<GroupResponseDto> {
    return this.http.put<GroupResponseDto>(`${this.baseUrl}/${orgCode}/groups/${groupId}`, request);
  }

  /**
   * Deletes a team group.
   * Endpoint: DELETE /api/v1/organizations/{orgCode}/groups/{groupId}
   */
  deleteGroup(orgCode: string, groupId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${orgCode}/groups/${groupId}`);
  }

  /**
   * Archives a team group.
   * Endpoint: POST /api/v1/organizations/{orgCode}/groups/{groupId}/archive
   */
  archiveGroup(orgCode: string, groupId: number): Observable<GroupResponseDto> {
    return this.http.post<GroupResponseDto>(`${this.baseUrl}/${orgCode}/groups/${groupId}/archive`, {});
  }

  /**
   * Unarchives a team group.
   * Endpoint: POST /api/v1/organizations/{orgCode}/groups/{groupId}/unarchive
   */
  unarchiveGroup(orgCode: string, groupId: number): Observable<GroupResponseDto> {
    return this.http.post<GroupResponseDto>(`${this.baseUrl}/${orgCode}/groups/${groupId}/unarchive`, {});
  }

  /**
   * Adds an active organization member to a team group.
   * Endpoint: POST /api/v1/organizations/{orgCode}/groups/{groupId}/members/{userId}?role={role}
   */
  addGroupMember(
    orgCode: string,
    groupId: number,
    userId: string,
    role?: 'MEMBER' | 'MANAGER'
  ): Observable<void> {
    let params = new HttpParams();
    if (role) {
      params = params.set('role', role);
    }
    return this.http.post<void>(
      `${this.baseUrl}/${orgCode}/groups/${groupId}/members/${userId}`,
      {},
      { params }
    );
  }

  /**
   * Updates a group member's role (MEMBER <-> MANAGER).
   * Endpoint: PUT /api/v1/organizations/{orgCode}/groups/{groupId}/members/{userId}/role
   */
  updateGroupMemberRole(
    orgCode: string,
    groupId: number,
    userId: string,
    request: UpdateGroupMemberRoleRequestDto
  ): Observable<void> {
    return this.http.put<void>(
      `${this.baseUrl}/${orgCode}/groups/${groupId}/members/${userId}/role`,
      request
    );
  }

  /**
   * Transfers team manager role to another active member.
   * Endpoint: POST /api/v1/organizations/{orgCode}/groups/{groupId}/transfer-manager
   */
  transferTeamManager(
    orgCode: string,
    groupId: number,
    newManagerUserId: string
  ): Observable<void> {
    return this.http.post<void>(
      `${this.baseUrl}/${orgCode}/groups/${groupId}/transfer-manager`,
      { newManagerUserId }
    );
  }

  /**
   * Removes a member from a team group.
   * Endpoint: DELETE /api/v1/organizations/{orgCode}/groups/{groupId}/members/{userId}
   */
  removeGroupMember(orgCode: string, groupId: number, userId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/${orgCode}/groups/${groupId}/members/${userId}`
    );
  }

  /**
   * Retrieves an organization by code.
   * Endpoint: GET /api/v1/organizations/{orgCode}
   */
  getOrganizationByCode(orgCode: string): Observable<OrganizationResponseDto> {
    return this.http.get<OrganizationResponseDto>(`${this.baseUrl}/${orgCode}`);
  }

  /**
   * Creates a new organization.
   * Endpoint: POST /api/v1/organizations
   */
  createOrganization(request: OrganizationRequestDto): Observable<OrganizationResponseDto> {
    return this.http.post<OrganizationResponseDto>(this.baseUrl, request);
  }

  /**
   * Adds member to organization.
   * Endpoint: POST /api/v1/organizations/{orgCode}/members
   */
  addMember(orgCode: string, request: AddMemberRequestDto): Observable<OrganizationMemberResponseDto> {
    return this.http.post<OrganizationMemberResponseDto>(`${this.baseUrl}/${orgCode}/members`, request);
  }

  /**
   * Gets organization members.
   * Endpoint: GET /api/v1/organizations/{orgCode}/members
   */
  getMembers(orgCode: string): Observable<OrganizationMemberResponseDto[]> {
    return this.http.get<OrganizationMemberResponseDto[]>(`${this.baseUrl}/${orgCode}/members`);
  }

  /**
   * Gets detailed organization members list with optional status filter and search query.
   * Endpoint: GET /api/v1/organizations/{orgCode}/members/details
   */
  getMemberDetails(
    orgCode: string,
    status?: string,
    query?: string
  ): Observable<OrganizationMemberDetailResponseDto[]> {
    let params = new HttpParams();
    if (status && status !== 'ALL') {
      params = params.set('status', status);
    }
    if (query && query.trim().length > 0) {
      params = params.set('q', query.trim());
    }
    return this.http.get<OrganizationMemberDetailResponseDto[]>(
      `${this.baseUrl}/${orgCode}/members/details`,
      { params }
    );
  }

  /**
   * Updates organization member role.
   * Endpoint: PUT /api/v1/organizations/{orgCode}/members/{userId}/role
   */
  updateMemberRole(
    orgCode: string,
    userId: string,
    request: UpdateMemberRoleRequestDto
  ): Observable<OrganizationMemberDetailResponseDto> {
    return this.http.put<OrganizationMemberDetailResponseDto>(
      `${this.baseUrl}/${orgCode}/members/${userId}/role`,
      request
    );
  }

  /**
   * Suspends an organization member.
   * Endpoint: POST /api/v1/organizations/{orgCode}/members/{userId}/suspend
   */
  suspendMember(
    orgCode: string,
    userId: string,
    request: MemberStatusChangeRequestDto
  ): Observable<OrganizationMemberDetailResponseDto> {
    return this.http.post<OrganizationMemberDetailResponseDto>(
      `${this.baseUrl}/${orgCode}/members/${userId}/suspend`,
      request
    );
  }

  /**
   * Reactivates a suspended organization member.
   * Endpoint: POST /api/v1/organizations/{orgCode}/members/{userId}/reactivate
   */
  reactivateMember(
    orgCode: string,
    userId: string,
    request?: MemberStatusChangeRequestDto
  ): Observable<OrganizationMemberDetailResponseDto> {
    return this.http.post<OrganizationMemberDetailResponseDto>(
      `${this.baseUrl}/${orgCode}/members/${userId}/reactivate`,
      request || {}
    );
  }

  /**
   * Removes member from organization.
   * Endpoint: POST /api/v1/organizations/{orgCode}/members/{userId}/remove
   */
  removeMember(
    orgCode: string,
    userId: string,
    request?: MemberStatusChangeRequestDto
  ): Observable<OrganizationMemberDetailResponseDto> {
    return this.http.post<OrganizationMemberDetailResponseDto>(
      `${this.baseUrl}/${orgCode}/members/${userId}/remove`,
      request ?? {}
    );
  }

  /**
   * Sends an organization invitation.
   * Endpoint: POST /api/v1/organizations/{orgCode}/invitations
   */
  createInvitation(
    orgCode: string,
    request: CreateOrganizationInvitationRequestDto
  ): Observable<OrganizationInvitationResponseDto> {
    return this.http.post<OrganizationInvitationResponseDto>(
      `${this.baseUrl}/${orgCode}/invitations`,
      request
    );
  }

  /**
   * Retrieves pending invitations for the authenticated user.
   * Endpoint: GET /api/v1/invitations/me
   */
  getMyPendingInvitations(): Observable<OrganizationInvitationResponseDto[]> {
    const url = `${environment.apiBaseUrl}/v1/invitations/me`;
    return this.http.get<OrganizationInvitationResponseDto[]>(url);
  }

  /**
   * Gets invitation details by token.
   * Endpoint: GET /api/v1/invitations/token/{token}
   */
  getInvitationByToken(token: string): Observable<OrganizationInvitationResponseDto> {
    const url = `${environment.apiBaseUrl}/v1/invitations/token/${token}`;
    return this.http.get<OrganizationInvitationResponseDto>(url);
  }

  /**
   * Accepts an organization invitation.
   * Endpoint: POST /api/v1/invitations/token/{token}/accept
   */
  acceptInvitation(token: string): Observable<OrganizationInvitationResponseDto> {
    const url = `${environment.apiBaseUrl}/v1/invitations/token/${token}/accept`;
    return this.http
      .post<OrganizationInvitationResponseDto>(url, {})
      .pipe(tap(() => this.refreshPendingCount()));
  }

  /**
   * Declines an organization invitation.
   * Endpoint: POST /api/v1/invitations/token/{token}/decline
   */
  declineInvitation(token: string): Observable<OrganizationInvitationResponseDto> {
    const url = `${environment.apiBaseUrl}/v1/invitations/token/${token}/decline`;
    return this.http
      .post<OrganizationInvitationResponseDto>(url, {})
      .pipe(tap(() => this.refreshPendingCount()));
  }

  /**
   * Transfers organization administration to another active member.
   * Endpoint: POST /api/v1/organizations/{orgCode}/transfer-admin
   */
  transferOrgAdmin(
    orgCode: string,
    newAdminUserId: string
  ): Observable<OrganizationMemberDetailResponseDto> {
    const payload: TransferOrgAdminRequestDto = { newAdminUserId };
    return this.http.post<OrganizationMemberDetailResponseDto>(
      `${this.baseUrl}/${orgCode}/transfer-admin`,
      payload
    );
  }

  /**
   * Retrieves pending invitations for an organization.
   * Endpoint: GET /api/v1/organizations/{orgCode}/invitations
   */
  getOrganizationInvitations(
    orgCode: string
  ): Observable<OrganizationInvitationResponseDto[]> {
    return this.http.get<OrganizationInvitationResponseDto[]>(
      `${this.baseUrl}/${orgCode}/invitations`
    );
  }

  /**
   * Revokes a pending organization invitation.
   * Endpoint: DELETE /api/v1/organizations/{orgCode}/invitations/{invitationId}
   */
  revokeInvitation(
    orgCode: string,
    invitationId: number
  ): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/${orgCode}/invitations/${invitationId}`
    );
  }
}


