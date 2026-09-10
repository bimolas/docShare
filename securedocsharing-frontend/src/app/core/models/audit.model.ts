export interface DocumentActivityLogResponse {
  id: number;
  action: string;
  timestamp: string;
  documentId?: number;
  documentFileHash?: string;
  folderId?: number;
  accessorEmail?: string;
  orgCode?: string;
  ipAddress?: string;
  userAgent?: string;
  details?: string;
  txHash?: string;
}

export interface PermissionAuditLogResponse {
  id: number;
  action: string;
  targetType: string;
  targetId: number;
  targetFileHash?: string;
  folderId?: number;
  spaceType?: string;
  orgCode?: string;
  grantedByEmail?: string;
  grantedToEmail?: string;
  grantedToOrgCode?: string;
  permissionLevel?: string;
  validFrom?: string;
  expiresAt?: string;
  timestamp: string;
}
