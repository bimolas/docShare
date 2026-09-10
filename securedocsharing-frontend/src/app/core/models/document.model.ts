export interface InitiateUploadRequest {
  fileName: string;
  contentType: string;
  fileSize: number;
  fileHash: string;
  folderId?: number | null;
  orgCode?: string | null;
}

export interface InitiateUploadResponse {
  documentId: number;
  objectKey: string;
  s3Bucket: string;
  presignedUploadUrl: string;
  expiresAt: string;
  status: string;
}

export interface ConfirmUploadRequest {
  clientHash?: string;
  objectKey?: string;
  fileName?: string;
  contentType?: string;
  fileSize?: number;
}

export interface DocumentResponse {
  id: number;
  fileName: string;
  contentType: string;
  fileSize: number;
  fileHash: string;
  s3Bucket?: string;
  objectKey?: string;
  status: string;
  spaceType: 'PERSONAL' | 'ORGANIZATION';
  ownerOrgCode?: string | null;
  uploadedByUserId: string;
  uploadedByUserEmail?: string;
  folderId?: number | null;
  folderName?: string | null;
  uploadedAt: string;
  version: number;
  accessStatus?: 'ACCESSIBLE' | 'LOCKED';
  isLocked?: boolean;
  requestStatus?: 'NONE' | 'PENDING';
  canRequestAccess?: boolean;
  pendingRequestId?: number | null;
}

export interface DownloadUrlResponse {
  documentId: number;
  downloadUrl: string;
  expiresAt: string;
  fileName: string;
}

export interface PreviewUrlResponse {
  documentId: number;
  previewUrl: string;
  expiresAt: string;
  fileName: string;
  contentType?: string;
}

export interface DocumentVersionResponse {
  id: number;
  versionNumber: number;
  fileName: string;
  fileSize: number;
  fileHash: string;
  uploadedByUserId: string;
  uploadedAt: string;
}

export interface DocumentMoveRequestDto {
  targetFolderId?: number | null;
}
