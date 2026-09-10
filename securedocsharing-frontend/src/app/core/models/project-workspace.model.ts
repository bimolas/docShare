import { OrganizationModel } from './organization.model';

export type ProjectWorkspaceStatus = 'ACTIVE' | 'ARCHIVED';

/**
 * Canonical Project Workspace Model representing a project workspace
 * within an organization.
 */
export interface ProjectWorkspaceModel {
  /**
   * Canonical numeric ID of the project workspace.
   * At runtime, this is ALWAYS the real numeric ProjectWorkspaceEntity.id.
   */
  id: number;
  /** Null for Personal Workspace, orgCode for Organization Workspace. */
  organizationCode: string | null;
  name: string;
  description?: string | null;
  rootFolderId: number;
  rootFolderName?: string | null;
  status: ProjectWorkspaceStatus;
  createdByUserId?: string | null;
  createdAt?: string;
  updatedAt?: string;
  isDefault?: boolean;
  /** @deprecated Temporary compatibility alias for organizationCode. */
  orgCode?: string | null;
  /** @deprecated Temporary compatibility discriminator for unmigrated UI components. */
  type?: 'ORGANIZATION' | 'PERSONAL';
}

/**
 * @deprecated Legacy dummy model. Canonical workspaces use ProjectWorkspaceModel.
 */
export interface PersonalWorkspaceModel {
  readonly id: number | 'personal';
  name: string;
  rootFolderId?: number;
  type?: 'PERSONAL';
  orgCode?: string | null;
}

export type ActiveWorkspaceModel = ProjectWorkspaceModel;

// ── Backend DTOs ─────────────────────────────────────────────────────────────

export interface ProjectWorkspaceResponseDto {
  id: number;
  orgCode?: string | null;
  name: string;
  description?: string | null;
  rootFolderId: number;
  rootFolderName?: string | null;
  status: ProjectWorkspaceStatus;
  createdByUserId?: string | null;
  createdAt?: string;
  updatedAt?: string;
  isDefault?: boolean;
}

export interface CreateProjectWorkspaceRequestDto {
  name: string;
  description?: string | null;
  orgCode?: string | null;
  rootFolderId?: number | null;
}

export interface CreatePersonalWorkspaceRequestDto {
  name: string;
  description?: string | null;
  rootFolderId?: number | null;
}

export interface UpdateProjectWorkspaceRequestDto {
  name?: string | null;
  description?: string | null;
}

export type WorkspacePermissionLevel = 'VIEW' | 'DOWNLOAD' | 'EDIT';
export type WorkspacePermissionType = 'ALLOW' | 'DENY';

export interface WorkspaceAssignmentResponseDto {
  permissionId: number;
  workspaceId: number;
  folderId?: number | null;
  folderName?: string | null;
  teamId?: number | null;
  teamName?: string | null;
  userEmail?: string | null;
  permissionLevel: WorkspacePermissionLevel;
  permissionType: WorkspacePermissionType;
  grantedByUserId?: string | null;
  grantedByUserEmail?: string | null;
  grantedAt?: string;
}

export interface WorkspaceTeamAssignmentRequestDto {
  teamId: number;
  folderId?: number | null;
  permissionLevel: WorkspacePermissionLevel;
  permissionType?: WorkspacePermissionType;
}

export interface WorkspaceUserAssignmentRequestDto {
  userEmail: string;
  folderId?: number | null;
  permissionLevel: WorkspacePermissionLevel;
  permissionType?: WorkspacePermissionType;
}

export interface WorkspacePolicyDto {
  workspaceType?: string;
  externalSharingMode?: 'OFF' | 'RESTRICTED' | 'UNRESTRICTED';
  discoverabilityMode?: 'DISCOVERABLE' | 'RESTRICTED' | 'HIDDEN';
  allowAccessRequests?: boolean;
  requireRequestReason?: boolean;
  allowExternalAccessRequests?: boolean;
  reviewerScopePolicy?: string;
  projectWorkspaceId?: number;
}

// ── Canonical Mappers ────────────────────────────────────────────────────────

export function toProjectWorkspaceModel(dto: ProjectWorkspaceResponseDto): ProjectWorkspaceModel {
  const isPersonal = !dto.orgCode;
  return {
    id: dto.id,
    organizationCode: dto.orgCode ?? null,
    orgCode: dto.orgCode ?? null,
    type: isPersonal ? 'PERSONAL' : 'ORGANIZATION',
    name: dto.name,
    description: dto.description ?? null,
    rootFolderId: dto.rootFolderId,
    rootFolderName: dto.rootFolderName ?? null,
    status: dto.status,
    createdByUserId: dto.createdByUserId ?? null,
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt,
    isDefault: dto.isDefault ?? false,
  };
}

export function toProjectWorkspaceModels(dtos: ProjectWorkspaceResponseDto[]): ProjectWorkspaceModel[] {
  return (dtos || []).map(toProjectWorkspaceModel);
}
