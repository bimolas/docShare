/**
 * Canonical frontend models for Shared With Me & Shared By Me.
 * Perfectly mapped to backend SharedItemDto, SharedWithMeResponseDto, and SharedByMeResponseDto.
 */

export type ShareResourceType = 'FOLDER' | 'DOCUMENT';
export type SharePermissionLevel = 'VIEW' | 'DOWNLOAD' | 'EDIT' | 'ADMIN';
export type SharePermissionType = 'ALLOW' | 'EXCLUDE' | 'DENY';
export type ShareSourceLabel =
  | 'DIRECT SHARE'
  | 'TEAM SHARE'
  | 'ORGANIZATION SHARE'
  | 'COLLABORATION SHARE';

export type ShareRecipientType =
  | 'USER'
  | 'GROUP'
  | 'ORGANIZATION'
  | 'COLLABORATION'
  | 'USER_PERSONAL'
  | 'USER_ORGANIZATION_MEMBER';

export type ShareLifecycleStatus = 'ACTIVE' | 'SUSPENDED' | 'REVOKED' | 'EXPIRED';

export interface SharedItem {
  permissionId: number;
  resourceType: ShareResourceType;
  resourceId: number;
  name: string;
  fileHash?: string | null;
  grantedByUserId?: string | null;
  grantedByUserEmail?: string | null;
  grantedByName?: string | null;
  permissionType: SharePermissionType;
  permissionLevel: SharePermissionLevel;
  grantedAt?: string | null;
  expiresAt?: string | null;
  note?: string | null;

  // Metadata enrichment
  sourceType?: string | null;
  ownerOrgCode?: string | null;
  ownerOrgName?: string | null;
  workspaceId?: number | null;
  workspaceName?: string | null;
  fileSize?: number | null;
  contentType?: string | null;
  external?: boolean | null;

  // Outbound / Recipient metadata
  recipientType?: ShareRecipientType | null;
  targetUserEmail?: string | null;
  targetUserName?: string | null;
  targetGroupId?: number | null;
  targetGroupName?: string | null;
  targetOrgCode?: string | null;
  targetOrgName?: string | null;
  collaborationId?: number | null;
  collaborationName?: string | null;
  status?: ShareLifecycleStatus | null;
  revoked?: boolean | null;
  suspended?: boolean | null;
  revokedAt?: string | null;

  // Multi-grant & provenance enrichment
  sourceLabel?: ShareSourceLabel | string | null;
  sourceLabels?: string[] | null;
  sourceDescription?: string | null;
  matchingGrantCount?: number | null;
  matchedPermissionIds?: number[] | null;
}

export interface SharedWithMeResponse {
  sharedFolders: SharedItem[];
  sharedDocuments: SharedItem[];
}

export interface SharedByMeResponse {
  sharedFolders: SharedItem[];
  sharedDocuments: SharedItem[];
}

export type SharingResourceTypeFilter = 'ALL' | 'FOLDERS' | 'DOCUMENTS';
export type SharingSourceFilter = 'ALL' | 'DIRECT' | 'TEAM' | 'ORGANIZATION' | 'COLLABORATION';
export type SharingLevelFilter = 'ALL' | 'EDIT' | 'DOWNLOAD' | 'VIEW';
export type SharingStatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE';
export type SharingViewMode = 'table' | 'cards';
