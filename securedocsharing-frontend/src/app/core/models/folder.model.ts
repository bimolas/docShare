export type SubspaceType = 'PERSONAL' | 'ORGANIZATION_GENERAL' | 'PROJECT_WORKSPACE' | 'COLLABORATION';

export interface FolderResponseDto {
  id: number;
  name: string;
  parentFolderId?: number | null;
  ownerOrgCode?: string | null;
  createdByUserId?: string | null;
  createdAt?: string | null;
  subspaceType?: SubspaceType | null;
  isDefaultSubspace?: boolean;
  accessStatus?: 'ACCESSIBLE' | 'LOCKED';
  isLocked?: boolean;
  requestStatus?: 'NONE' | 'PENDING';
  canRequestAccess?: boolean;
  pendingRequestId?: number | null;
}

export interface FolderBreadcrumbDto {
  id: number;
  name: string;
}

export interface FolderRequestDto {
  name: string;
  parentFolderId?: number | null;
  subspaceType?: SubspaceType | null;
}

export interface FolderContentResponseDto {
  currentFolder: FolderResponseDto;
  breadcrumbs: FolderBreadcrumbDto[];
  subFolders: FolderResponseDto[];
  documents: Array<{
    id: number | string;
    name?: string;
    fileName?: string;
    folderId?: number;
    fileSizeBytes?: number;
    fileSize?: number;
    mimeType?: string;
    contentType?: string;
    createdAt?: string;
    uploadedAt?: string;
    updatedAt?: string;
    fileHash?: string;
    uploadedByUserId?: string;
    uploadedByUserEmail?: string;
    createdByUserId?: string;
    status?: string;
    version?: number;
    spaceType?: 'PERSONAL' | 'ORGANIZATION';
    ownerOrgCode?: string | null;
    accessStatus?: 'ACCESSIBLE' | 'LOCKED';
    isLocked?: boolean;
    requestStatus?: 'NONE' | 'PENDING';
    canRequestAccess?: boolean;
    pendingRequestId?: number | null;
  }>;
}

export interface FolderPeekResponseDto {
  folderId: number;
  name?: string;
  folderName?: string;
  subfolderCount?: number;
  totalSubfolders?: number;
  fileCount?: number;
  totalDocuments?: number;
  totalSizeBytes: number;
  parentFolderId?: number | null;
  ownerOrgCode?: string | null;
  createdByUserId?: string;
  createdByEmail?: string;
  ownerEmail?: string;
  createdAt?: string;
  currentUserPermission?: string;
  topItems?: any[];
}

