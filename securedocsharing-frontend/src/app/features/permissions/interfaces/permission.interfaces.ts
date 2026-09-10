import { PermissionLevel } from '../../../core/config/app.constants';
import { UserOrganizationMembershipDto, UserResponseDto } from '../../../core/models/user.model';

export type { UserOrganizationMembershipDto };

export type RecipientType = 'USER_PERSONAL' | 'USER_ORGANIZATION_MEMBER' | 'ORGANIZATION' | 'GROUP';

export interface SelectedRecipient {
  selectionKey: string;
  recipientType: RecipientType;
  targetUserEmail?: string;
  targetOrgCode?: string;
  targetGroupId?: number;
  displayName: string;
  displayContext: string;
  orgRole?: string;
  userProfile?: UserResponseDto;
}

export function getRecipientSelectionKey(
  recipientType: RecipientType,
  targetUserEmail?: string | null,
  targetOrgCode?: string | null,
  targetGroupId?: number | string | null
): string {
  const emailLower = (targetUserEmail || '').toLowerCase().trim();
  const orgCodeLower = (targetOrgCode || '').toLowerCase().trim();

  if (recipientType === 'USER_ORGANIZATION_MEMBER' && orgCodeLower) {
    return `USER_ORGANIZATION_MEMBER:${orgCodeLower}:${emailLower}`;
  }
  if (recipientType === 'ORGANIZATION' && orgCodeLower) {
    return `ORGANIZATION:${orgCodeLower}`;
  }
  if (recipientType === 'GROUP' && targetGroupId != null && targetGroupId !== '') {
    return `GROUP:${targetGroupId}`;
  }
  return `USER_PERSONAL:${emailLower}`;
}



// ── Permission Entry ───────────────────────────────────────────────────────

export type ResourceType = 'FILE' | 'FOLDER';
export type PermissionType = 'ALLOW' | 'EXCLUDE';
export type SharePermissionLevel = 'VIEW' | 'DOWNLOAD' | 'EDIT';

export interface PermissionEntry {
  id: string | number;
  userId?: string | number;
  userEmail?: string;
  userFullName?: string;
  targetUserEmail?: string;
  targetOrgCode?: string;
  targetGroupId?: number | string;
  recipientType?: RecipientType;
  entityType?: 'USER' | 'GROUP' | 'ORGANIZATION';
  entityName?: string;
  resourceId?: string | number;
  resourceName?: string;
  resourceType?: ResourceType | string;
  permissionLevel: PermissionLevel | SharePermissionLevel | string;
  grantType?: 'DIRECT' | 'INHERITED' | 'ORGANIZATION' | 'OWNER';
  inheritedFromId?: string | number;
  inheritedFromFolderName?: string;
  expiresAt?: string;
  grantedAt?: string;
  active?: boolean;
}

export interface UserAccessSummary {
  userId?: number | string;
  email?: string;
  fullName?: string;
  effectiveAccessLevel?: string;
  accessSource?: string;
  inheritedFromFolderId?: number;
  inheritedFromFolderName?: string;
}

export interface EffectivePermissionsResponse {
  resourceId?: string | number;
  resourceType?: ResourceType | string;
  resourceName?: string;
  ownerEmail?: string;
  userAccessSummaries?: UserAccessSummary[];
  directPermissions?: PermissionEntry[] | PermissionResponseDto[];
  inheritedPermissions?: PermissionEntry[] | PermissionResponseDto[];
  activeShareLinks?: ShareLinkResponseDto[];
}

// ── Backend DTOs ────────────────────────────────────────────────────────────

export interface PermissionRequestDto {
  permissionType: PermissionType;
  permissionLevel: SharePermissionLevel;
  documentId?: number;
  documentFileHash?: string;
  folderId?: number;
  targetUserEmail?: string;
  targetOrgCode?: string;
  targetGroupId?: number;
  recipientType?: RecipientType;
  passwordHash?: string;
  expiresAt?: string;
  collaborationId?: number;
  note?: string;
}

export interface PermissionResponseDto {
  id: number;
  permissionType: PermissionType;
  permissionLevel: SharePermissionLevel;
  documentId?: number;
  documentFileHash?: string;
  documentName?: string;
  folderId?: number;
  folderName?: string;
  subspaceType?: string;
  spaceName?: string;
  subspaceName?: string;
  orgName?: string;
  targetUserEmail?: string;
  targetOrgCode?: string;
  targetGroupId?: number;
  recipientType?: RecipientType;
  grantedByUserId?: string;
  grantedAt?: string;
  expiresAt?: string;
  active: boolean;
  suspended?: boolean;
  revoked?: boolean;
  revokedAt?: string;
  revokedByUserId?: string;
  inherited?: boolean;
}

export interface ShareLinkRequestDto {
  documentId?: number;
  folderId?: number;
  permissionType?: PermissionType;
  permissionLevel: SharePermissionLevel;
  password?: string;
  expiresInHours?: number;
  targetUserEmail?: string;
}

export interface ShareLinkResponseDto {
  id: number;
  token?: string | null;
  documentId?: number;
  documentName?: string;
  folderId?: number;
  folderName?: string;
  permissionType: PermissionType;
  permissionLevel: SharePermissionLevel;
  passwordProtected: boolean;
  expiresAt?: string;
  createdAt: string;
  active: boolean;
  createdByUserId?: string;
  createdByUserEmail?: string;
  canReveal?: boolean;
}

export interface ShareLinkRevealResponseDto {
  permissionId: number;
  shareLinkToken: string;
  shareUrl: string;
  expiresAt?: string;
}

// ── Grant Requests ─────────────────────────────────────────────────────────

export interface GrantPermissionRequest {
  resourceId: string;
  resourceType: ResourceType;
  userId: string;
  permissionLevel: PermissionLevel;
  expiresAt?: string;
}

export interface BulkGrantRequest {
  resourceId: string;
  resourceType: ResourceType;
  userEmails: string[];
  permissionLevel: PermissionLevel;
  excludedChildIds?: string[];
}

export interface OrgWideGrantRequest {
  resourceId: string;
  resourceType: ResourceType;
  permissionLevel: PermissionLevel;
  excludedUserIds?: string[];
}

// ── Update / Revoke ────────────────────────────────────────────────────────

export interface UpdatePermissionRequest {
  permissionLevel: PermissionLevel;
  expiresAt?: string;
}

export interface SharedWithMeItem {
  resourceId: string;
  resourceName: string;
  resourceType: ResourceType;
  permissionLevel: PermissionLevel;
  ownerEmail: string;
  ownerFullName: string;
  sharedAt: string;
  lastAccessedAt?: string;
}

export interface BulkShareWithExclusionsDto {
  folderId: number;
  targetUserEmail?: string;
  targetOrgCode?: string;
  targetGroupId?: number;
  permissionLevel: SharePermissionLevel | string;
  recipientType?: RecipientType;
  excludedDocumentIds?: number[];
  excludedSubfolderIds?: number[];
  expiresAt?: string;
  note?: string;
}

export interface EffectiveAccessEntryDto {
  permissionId?: number;
  principalType?: 'USER' | 'TEAM' | 'ORGANIZATION' | 'COLLABORATION' | string;
  principalId?: string;
  displayName?: string;
  email?: string;
  permissionLevel?: SharePermissionLevel | string;
  permissionType?: PermissionType | string;
  source?: 'DIRECT' | 'TEAM' | 'ORGANIZATION' | 'WORKSPACE' | 'COLLABORATION' | 'INHERITED' | 'EXCLUDED' | 'OWNER' | string;
  inheritedFromResourceId?: number;
  inheritedFromResourceName?: string;
  expiresAt?: string;
  external?: boolean;
  canRevoke?: boolean;
  status?: 'ACTIVE' | 'EXPIRED' | 'REVOKED' | string;
}

export interface ManageAccessSummaryDto {
  resourceId?: number;
  resourceType?: 'DOCUMENT' | 'FOLDER' | string;
  resourceName?: string;
  ownerUserId?: string;
  ownerDisplayName?: string;
  ownerEmail?: string;
  ownerOrgCode?: string;
  entries?: EffectiveAccessEntryDto[];
}



