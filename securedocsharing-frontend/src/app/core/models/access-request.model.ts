export type AccessRequestStatus = 'PENDING' | 'APPROVED' | 'DENIED' | 'CANCELLED';
export type AccessPermissionLevel = 'VIEW' | 'DOWNLOAD' | 'EDIT';

export interface AccessRequestResponseDto {
  id: number;
  requesterUserId?: string;
  requesterUserEmail?: string;
  requesterUserName?: string;
  targetType: 'DOCUMENT' | 'FOLDER';
  documentId?: number;
  documentFileHash?: string;
  documentName?: string;
  folderId?: number;
  folderName?: string;
  requestedPermissionLevel: AccessPermissionLevel;
  status: AccessRequestStatus;
  reason?: string;
  processedByUserId?: string;
  processedByUserEmail?: string;
  processedAt?: string;
  reviewerNote?: string;
  cancellationReason?: string;
  reviewerAuthorityExplanation?: string;
  grantedPermissionId?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface AccessRequestCreateDto {
  documentId?: number;
  documentFileHash?: string;
  folderId?: number;
  requestedPermissionLevel: AccessPermissionLevel;
  reason?: string;
}

export interface AccessRequestEligibilityDto {
  eligible: boolean;
  reasonCode?: string;
  requireReason?: boolean;
  existingRequestId?: number;
  currentPermissionLevel?: AccessPermissionLevel;
}

export interface AccessRequestActionDto {
  reviewerNote?: string;
  grantedPermissionLevel?: AccessPermissionLevel;
}
