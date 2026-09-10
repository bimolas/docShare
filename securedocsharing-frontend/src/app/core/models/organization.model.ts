export interface OrganizationModel {
  orgCode: string;
  name: string;
  role?: string;
  status?: string;
}

export interface OrganizationResponseDto {
  orgCode: string;
  name: string;
  createdByUserId?: string;
  createdAt: string;
}

export interface OrganizationRequestDto {
  orgCode: string;
  name: string;
}

export interface OrganizationMemberResponseDto {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  groupRole?: 'MEMBER' | 'MANAGER' | string;
  joinedAt: string;
}

export interface OrganizationMemberDetailResponseDto {
  userId: string;
  email: string;
  firstName?: string;
  lastName?: string;
  orgCode: string;
  role: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'REMOVED';
  joinedAt: string;
  statusUpdatedAt?: string;
  updatedByUserId?: string;
  statusReason?: string;
}

export interface AddMemberRequestDto {
  email: string;
  role: string;
}

export interface UpdateMemberRoleRequestDto {
  role: 'ROLE_USER' | 'ROLE_ORG_MANAGER' | 'ROLE_ORG_ADMIN';
}

export interface TransferOrgAdminRequestDto {
  newAdminUserId: string;
}

export interface MemberStatusChangeRequestDto {
  reason?: string;
}

export interface GroupResponseDto {
  id: number;
  name: string;
  description?: string;
  orgCode: string;
  status?: string;
  memberCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface GroupRequestDto {
  name: string;
  description?: string;
  initialManagerUserId?: string;
  initialMemberUserIds?: string[];
}

export interface UpdateGroupMemberRoleRequestDto {
  role: 'MEMBER' | 'MANAGER' | string;
}

export interface CreateOrganizationInvitationRequestDto {
  inviteeEmail: string;
  role: 'ROLE_USER' | 'ROLE_ORG_ADMIN' | 'ROLE_ORG_MANAGER' | string;
  note?: string;
  expirationDays?: number;
}

export interface OrganizationInvitationResponseDto {
  id: number;
  token: string;
  orgCode: string;
  organizationName?: string;
  inviterUserId?: string;
  inviterEmail?: string;
  inviteeEmail: string;
  inviteeUserId?: string;
  role: string;
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED' | 'REVOKED' | string;
  createdAt: string;
  expiresAt?: string;
  respondedAt?: string;
  note?: string;

  // Attached Onboarding Assignment Metadata
  targetGroupId?: number;
  targetGroupName?: string;
  targetGroupRole?: string;
  targetWorkspaceId?: number;
  targetWorkspaceName?: string;
  targetFolderId?: number;
  targetDocumentHash?: string;
  onboardingPermissionLevel?: string;
}

export interface TeamSummaryDto {
  groupId: number;
  groupName: string;
  role: 'LEADER' | 'MANAGER' | 'MEMBER' | string;
}

export interface WorkspaceSummaryDto {
  workspaceId: string;
  name: string;
  accessType?: string;
}

export interface DirectPermissionSummaryDto {
  permissionId: number;
  targetType: string;
  targetId: number;
  targetName: string;
  permissionLevel: 'VIEW' | 'DOWNLOAD' | 'EDIT' | string;
}

export interface MemberAccessSummaryResponseDto {
  userId: string;
  email: string;
  orgCode: string;
  orgRole: string;
  status: 'ACTIVE' | 'SUSPENDED' | 'PENDING' | 'REMOVED' | string;
  teams: TeamSummaryDto[];
  workspaces: WorkspaceSummaryDto[];
  directPermissions: DirectPermissionSummaryDto[];
}
