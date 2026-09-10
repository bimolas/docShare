export type TeamPermissionLevel = 'VIEW' | 'DOWNLOAD' | 'EDIT';

export interface TeamAccessBoundaryDto {
  permissionId?: number;
  teamId: number;
  teamName?: string;
  workspaceId: number;
  workspaceName: string;
  folderId?: number;
  folderName?: string;
  folderPath?: string;
  rootFolderId?: number;
  rootFolderName?: string;
  rootFolderPath?: string;
  ceilingLevel?: TeamPermissionLevel;
  permissionLevel?: TeamPermissionLevel;
  permissionType?: string;
  status?: 'ACTIVE' | 'SUSPENDED' | 'REVOKED';
  allocatedByUserId?: string;
  allocatedByUserEmail?: string;
  allocatedAt?: string;
  assignedByUserId?: string;
  assignedByUserEmail?: string;
  assignedAt?: string;
}

export interface TeamMemberAccessDto {
  permissionId: number;
  teamId: number;
  teamName: string;
  userEmail: string;
  userName?: string;
  resourceId: number;
  resourceType: 'FOLDER' | 'DOCUMENT' | 'UNKNOWN';
  resourceName: string;
  resourcePath?: string;
  permissionLevel: TeamPermissionLevel;
  grantorAuthorityTier?: string;
  grantedByUserId?: string;
  grantedByUserEmail?: string;
  grantedAt?: string;
  isMigrated: boolean;
  note?: string;
}

export interface GrantMemberAccessRequestDto {
  userEmail: string;
  folderId?: number;
  documentId?: number;
  permissionLevel: TeamPermissionLevel;
  note?: string;
}

export interface UpdateMemberAccessRequestDto {
  permissionLevel: TeamPermissionLevel;
  note?: string;
}

export interface ScopedResourceNodeDto {
  id: number;
  name: string;
  type: 'FOLDER' | 'DOCUMENT';
  permissionCeiling: TeamPermissionLevel;
  parentFolderId?: number;
  children: ScopedResourceNodeDto[];
  documentCount?: number;
  path?: string;
}
