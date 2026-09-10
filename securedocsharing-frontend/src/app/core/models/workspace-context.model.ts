import { OrganizationModel } from './organization.model';
import {
  ProjectWorkspaceModel,
  PersonalWorkspaceModel,
  ActiveWorkspaceModel,
} from './project-workspace.model';

export type WorkspaceType = 'PERSONAL' | 'ORGANIZATION';

/**
 * Canonical Workspace Context Model.
 * Represents the clean domain separation:
 * Organization -> ProjectWorkspace -> rootFolderId
 */
export interface CanonicalWorkspaceContext {
  activeOrganization: OrganizationModel | null;
  activeWorkspace: ActiveWorkspaceModel | null;
  availableOrganizations: OrganizationModel[];
  availableWorkspaces: ProjectWorkspaceModel[];
  activeRootFolderId: number | null;
  isLoading: boolean;
  error: string | null;
}

/**
 * @deprecated Temporary legacy compatibility interface for unmigrated UI components.
 * Will be removed in cleanup phase 4A.6.C.G.
 */
export interface WorkspaceInfo {
  id: string; // 'personal' or orgCode (compatibility)
  name: string;
  type: WorkspaceType;
  orgCode?: string;
  orgId?: string;
}

/**
 * @deprecated Temporary legacy compatibility interface for unmigrated UI components.
 * Will be removed in cleanup phase 4A.6.C.G.
 */
export interface SubspaceInfo {
  id: string; // folderId stringified or subspace UUID
  folderId: number;
  name: string;
  workspaceId: string; // 'personal' or orgCode
  isDefault: boolean;
  description?: string;
  createdAt?: string;
}

/**
 * @deprecated Legacy context shape retained for compatibility.
 */
export interface WorkspaceContext {
  activeWorkspace: WorkspaceInfo | null;
  activeSubspace: SubspaceInfo | null;
  availableWorkspaces: WorkspaceInfo[];
  availableSubspaces: SubspaceInfo[];
  isLoading: boolean;
  error: string | null;
}
