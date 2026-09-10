import { PermissionLevel } from '../../../core/config/app.constants';

// ── Document / File ────────────────────────────────────────────────────────

export interface DocumentMetadata {
  id: string;
  name: string;
  extension: string;
  mimeType: string;
  sizeBytes: number;
  folderId: string | null;
  spaceId: string;
  ownerId: string;
  ownerEmail: string;
  createdAt: string;
  updatedAt: string;
  sha256Hash: string;
  encryptedAtRest: boolean;
  kmsKeyId?: string;
}

export interface DocumentVersion {
  versionNumber: number;
  uploadedAt: string;
  uploadedByEmail: string;
  sizeBytes: number;
  downloadUrl?: string;
}

export interface DocumentAccessEntry {
  userId: string;
  userEmail: string;
  userFullName: string;
  permissionLevel: PermissionLevel;
  grantType: 'DIRECT' | 'INHERITED';
  inheritedFrom?: string;
  grantedAt: string;
}

// ── Folder ─────────────────────────────────────────────────────────────────

export interface FolderMetadata {
  id: string;
  name: string;
  parentId: string | null;
  spaceId: string;
  ownerId: string;
  totalSizeBytes: number;
  fileCount: number;
  subfolderCount: number;
  encryptionRatio: number;
  createdAt: string;
  updatedAt: string;
  hasInheritedPermissions: boolean;
}

export interface FolderContentsResponse {
  folder: FolderMetadata;
  breadcrumbs: BreadcrumbItem[];
  subfolders: FolderMetadata[];
  files: DocumentMetadata[];
}

export interface BreadcrumbItem {
  id: string;
  name: string;
  path: string;
}

// ── Explorer Requests ──────────────────────────────────────────────────────

export interface FolderContentsRequest {
  spaceId?: string;
  folderId?: string | null;
  page?: number;
  size?: number;
}

export interface CreateFolderRequest {
  name: string;
  parentId: string | null;
  spaceId: string;
}

export interface MoveFolderRequest {
  targetParentId: string | null;
}
